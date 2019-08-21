/*******************************************************************************
 * BSD 3-Clause License
 *
 * Copyright (c) 2017 Beshr Al Nahas and Olaf Landsiedel.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
/*
 * \file
 *         Join library.
 * \author
 *         Beshr Al Nahas <beshr@chalmers.se>
 *         Olaf Landsiedel <olafl@chalmers.se>
 */

#include "contiki.h"
#include <string.h>
#include <stdio.h>

#include "chaos.h"
#include "chaos-random-generator.h"
#include "chaos-control.h"
#include "node.h"
#include "join.h"
#include "testbed.h"
#include "chaos-config.h"

#define ENABLE_COOJA_DEBUG COOJA
#include "dev/cooja-debug.h"

#if 1 //FAULTY_NODE_ID
volatile uint8_t rx_pkt_crc_err[129] = {0};
volatile uint8_t rx_pkt_copy[129] = {0};
volatile join_debug_t join_debug_var = {0,0,0,0,0};
#endif

#ifndef JOIN_STRESS_TEST
#define JOIN_STRESS_TEST 0
#endif

#ifndef JOIN_ROUNDS_AFTER_BOOTUP
#define JOIN_ROUNDS_AFTER_BOOTUP (10)
#endif

#define JOIN_SLOT_LEN          (7*(RTIMER_SECOND/1000)+0*(RTIMER_SECOND/1000)/2)    //TODO needs calibration
#define JOIN_SLOT_LEN_DCO      (JOIN_SLOT_LEN*CLOCK_PHI)    //TODO needs calibration

#define JOIN_MAX_COMMIT_SLOT (JOIN_ROUND_MAX_SLOTS / 2)

#ifndef COMMIT_THRESHOLD
#define COMMIT_THRESHOLD (JOIN_MAX_COMMIT_SLOT)
#endif

#ifndef N_TX_COMPLETE
#define N_TX_COMPLETE 9
#endif

#ifndef CHAOS_RESTART_MIN
#define CHAOS_RESTART_MIN 3
#endif

#ifndef CHAOS_RESTART_MAX
#define CHAOS_RESTART_MAX 10
#endif



#ifndef JOIN_TEST_LEAVE_THRESHOLD
#define JOIN_TEST_LEAVE_THRESHOLD (JOIN_ROUNDS_AFTER_BOOTUP+10)
#endif

#define FLAGS_LEN(node_count)   ((node_count / 8) + ((node_count % 8) ? 1 : 0))
#define LAST_FLAGS(node_count)  ((1 << ((((node_count) - 1) % 8) + 1)) - 1)
#define FLAG_SUM(node_count)  ((((node_count) - 1) / 8 * 0xFF) + LAST_FLAGS(node_count))

typedef struct __attribute__((packed)) {
  join_data_t data;
  uint8_t flags[FLAGS_LEN(MAX_NODE_COUNT)];  //flags used to confirm the commit
} join_t;

#if JOIN_LOG_FLAGS
uint8_t chaos_join_flags_log[JOIN_ROUND_MAX_SLOTS]={0};
uint8_t chaos_join_commit_log[JOIN_ROUND_MAX_SLOTS]={0};
#endif



//local state
static uint8_t complete = 0;
static uint8_t invalid_rx_count = 0;
static uint8_t tx_timeout_enabled = 0;
static uint8_t tx_count_complete = 0;
static uint8_t delta_at_slot = 0;


//static uint8_t got_valid_rx = 0;
static uint8_t pending = 0;
static uint16_t commit_slot = 0;
static uint16_t off_slot = 0;
static uint16_t complete_slot = 0;


//initiator management
static uint8_t is_join_round = 0; // only used on initiator


node_index_t free_slots[MAX_NODE_COUNT] = {0};
node_id_t joined_nodes[MAX_NODE_COUNT] = {0};
uint8_t join_config = 0;
static join_node_map_entry_t joined_nodes_map[MAX_NODE_COUNT];

static void round_begin(const uint16_t round_count, const uint8_t id);
static int is_pending(const uint16_t round_count);
static void round_begin_sniffer(chaos_header_t* header);
static void round_end_sniffer(const chaos_header_t* header);
static int join_binary_search_chaos_index( join_node_map_entry_t array[], int size, node_id_t search_id );
static void join_merge_sort(join_node_map_entry_t a[], join_node_map_entry_t aux[], int lo, int hi);

// TODO: Use the join config in the join and leave rounds as well
uint8_t join_get_config() {
  return join_config;
}

void join_set_config(uint8_t config) {
  join_config = config;
}

void join_increase_config() {
  join_config++;
}

uint8_t join_check_config(uint8_t other) {
  return join_config == other;
}


CHAOS_SERVICE(join, JOIN_SLOT_LEN, JOIN_ROUND_MAX_SLOTS, 0, is_pending, round_begin, round_begin_sniffer, round_end_sniffer);

static uint8_t bit_count(uint8_t u)
{
  return  (u -(u>>1)-(u>>2)-(u>>3)-(u>>4)-(u>>5)-(u>>6)-(u>>7));
}

void join_do_sort_joined_nodes_map(){
  LEDS_ON(LEDS_RED);
  //need to do a precopy!!

  join_node_map_entry_t tmp[MAX_NODE_COUNT];
  memcpy(tmp, joined_nodes_map, sizeof(joined_nodes_map));
  join_merge_sort(joined_nodes_map, tmp, 0, chaos_node_count-1);

  LEDS_OFF(LEDS_RED);
}

void join_print_nodes(void){
  int i;
  for( i=0; i< chaos_node_count; i++ ){
    printf("%u:%u%s", joined_nodes_map[i].node_id, joined_nodes_map[i].chaos_index, (((i+1) & 7) == 0) ? "\n" : ", " );
  }
}

void join_init(){
  //clear node information
  chaos_node_index = 0;
  chaos_node_count = 0;
  chaos_has_node_index = 0;

  //clear local state
  commit_slot = 0;
  off_slot = JOIN_ROUND_MAX_SLOTS;
  complete_slot = 0;
  complete = 0;
  invalid_rx_count = 0;
  tx_timeout_enabled = 0;
  tx_count_complete = 0;
  delta_at_slot = 0;
  pending = 0;
  is_join_round = 0;

  //initiator management
  memset(&joined_nodes_map, 0, sizeof(joined_nodes_map));

  if( IS_INITIATOR( ) ){
    chaos_has_node_index = 1;
    chaos_node_index = 0;
    joined_nodes[0] = node_id;
    joined_nodes_map[0].chaos_index=0;
    joined_nodes_map[0].node_id=node_id;
    chaos_node_count = 1;
    join_init_free_slots();
  }
}

inline int join_merge_lists(node_id_t merge[], uint8_t max, node_id_t ids_a[], uint8_t ca, node_id_t ids_b[], uint8_t cb, uint8_t * delta) {

  uint8_t index_merge = 0;
  uint8_t index_a = 0;
  uint8_t index_b = 0;

  // We merge both sorted ids
  uint8_t equal_count = 0;

  while((index_a < ca || index_b < cb) && index_merge < max) {
    if (index_a >= ca || (index_b < cb && ids_b[index_b] < ids_a[index_a])) {
      merge[index_merge] = ids_b[index_b];
      index_b++;
    } else if (index_b >= cb || (index_a < ca && ids_a[index_a] < ids_b[index_b])) {
      merge[index_merge] = ids_a[index_a];
      index_a++;
    } else {
      merge[index_merge] = ids_a[index_a];
      equal_count++;
      index_a++;
      index_b++;
    }

    index_merge++;

#if 0*FAULTY_NODE_ID
    //TODO: Enable me again
    //if( merge[index_merge-1] > FAULTY_NODE_ID && !rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] ){
    //    rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1]=3;
    //    memcpy(rx_pkt_crc_err, (uint8_t*)join_rx, sizeof(join_t));
    //  }
#endif
  }

  if (delta) {
    *delta |= equal_count != index_merge;
  }

  return index_merge;
}

// only executed by initiator
// returns -1 if an overflow occured, else the chaos index
inline int add_node(node_id_t id, uint8_t chaos_node_count_before_commit) {
  // search and check if this is node is already added
  LEDS_ON(LEDS_RED);

  int j = join_binary_search_chaos_index(joined_nodes_map, chaos_node_count_before_commit, id);
  LEDS_OFF(LEDS_RED);

  if( j > -1 ){
    // index of this node is j
    return j;
  }

  // add only if we have have space for it
  if(chaos_node_count < MAX_NODE_COUNT) {
    node_index_t chaos_index = free_slots[MAX_NODE_COUNT-1-chaos_node_count];
    joined_nodes[chaos_index] = id;

    // joined_nodes_map will be sorted later
    joined_nodes_map[chaos_node_count].node_id = id;
    joined_nodes_map[chaos_node_count].chaos_index = chaos_index;
    chaos_node_count++;
    return chaos_index;
  } else {
    return -1;
  }
}


void join_init_free_slots() {
  // we can calculate the number of free slots:
  uint8_t num_free = 0;

  int i;
  // the free slots are used in reverse
  for(i= MAX_NODE_COUNT-1; i >= 0; --i) {
    node_id_t nid = joined_nodes[i];
    if (nid == 0) {
      // this slot is free!
      free_slots[num_free] = i;
      num_free++;
    }
  }

  if (num_free != MAX_NODE_COUNT-chaos_node_count) {
      printf("leaves Num free not matching!\n");
  }


  /*i = chaos_node_count;
  while(i < MAX_NODE_COUNT) {
    node_index_t chaos_index = free_slots[MAX_NODE_COUNT-1-i];
    printf("Free Slots: %d, %d\n", chaos_index, MAX_NODE_COUNT-1-i);
    ++i;
  }*/
}


void join_reset_nodes_map() {

  uint8_t map_count = 0;

  int i;
  for(i= 0; i < MAX_NODE_COUNT; ++i) {
    node_id_t nid = joined_nodes[i];

    if (nid > 0) {
      joined_nodes_map[map_count].node_id = nid;
      joined_nodes_map[map_count].chaos_index = i;
      map_count++;
    }
  }

  if (map_count != chaos_node_count) {
    printf("leaves Not matching count");
  }
  join_do_sort_joined_nodes_map();
}

//only executed by initiator
static inline void commit(join_t* join_tx) {
  COOJA_DEBUG_STR("commit!");
  uint8_t chaos_node_count_before_commit = chaos_node_count;

  join_data_t *join_data_tx = &join_tx->data;
  int i;
  for (i = 0; i < join_data_tx->slot_count; i++) {
    // TODO: We dont need to check for the indices, because, they all want to join right?
    if( !join_data_tx->indices[i] && join_data_tx->slots[i] ){
      int chaos_index = add_node(join_data_tx->slots[i], chaos_node_count_before_commit);
      if (chaos_index >= 0) {
        join_data_tx->indices[i] = chaos_index;
      } else {
        join_data_tx->overflow |= 1;
        //TODO: we should reset the slot so that the nodes wont use index 0 as chaos index?
      }
    }
  }
  //reset flags
  memset(join_tx->flags, 0, sizeof(join_tx->flags));
  //commit on my flag
  unsigned int array_index = chaos_node_index / 8;
  unsigned int array_offset = chaos_node_index % 8;
  join_tx->flags[array_index] |= 1 << (array_offset);
  //update phase and node_count
  join_data_tx->node_count = chaos_node_count;
  join_data_tx->commit = 1;
}

static chaos_state_t process(uint16_t round_count, uint16_t slot,
    chaos_state_t current_state, int chaos_txrx_success, size_t payload_length,
    uint8_t* rx_payload, uint8_t* tx_payload, uint8_t** app_flags){
  join_t* join_tx = (join_t*) tx_payload;
  join_t* join_rx = (join_t*) rx_payload;
  
  join_data_t* join_data_tx = &join_tx->data;
  join_data_t* join_data_rx = &join_rx->data;

  uint8_t delta = 0;
  uint16_t flag_sum = 0;

#if 0*FAULTY_NODE_ID /*|| FAULTY_NODE_COUNT*/
  int i = 0;
  //check join_rx
  if( current_state == CHAOS_RX && chaos_txrx_success ){
    for(i=0; i<join_data_rx->slot_count && !join_debug_var.slot && rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] != 1; i++){
      if(join_data_rx->slots[i] > FAULTY_NODE_ID || join_data_rx->node_count > FAULTY_NODE_COUNT){
        rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1]=4;
        memcpy(rx_pkt_crc_err, rx_payload, payload_length);
        join_debug_var.debug_pos=__LINE__;
        join_debug_var.info |= RX_ERR;
        break;
      }
    }
  }
  //check join_tx
  for(i=0; i<join_data_tx->slot_count && !join_debug_var.slot && rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] != 1; i++){
    if(join_data_tx->slots[i] > FAULTY_NODE_ID ||  join_data_tx->node_count > FAULTY_NODE_COUNT){
      rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1]=5;
      memcpy(rx_pkt_crc_err, tx_payload, payload_length);
      join_debug_var.debug_pos=__LINE__;
      join_debug_var.info |= TX_ERR_BEFORE;
      break;
    }
  }
  //check dummy for zeros
  uint32_t *dummy_packet_32t = chaos_get_dummy_packet_32t();
  for(i=0; i<(RADIO_MAX_PACKET_LEN + 3)/ 4
          && !join_debug_var.slot
          && rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] != 1;
      i++){
    if(dummy_packet_32t[i] != 0){
      rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] |= 0x40;
      join_debug_var.debug_pos=__LINE__;
      join_debug_var.info |= DUMMY_ERR_BEFORE;
      break;
    }
  }
#endif

  if( current_state == CHAOS_RX && chaos_txrx_success ){

    //process overflow flag
    delta |= (join_data_tx->overflow != join_data_rx->overflow);
    join_data_tx->overflow |= join_data_rx->overflow;
    join_data_tx->node_count = MAX(join_data_rx->node_count, join_data_tx->node_count);

    //not yet committed
    if (join_data_tx->commit == 0 && join_data_rx->commit == 0) {
      if( IS_INITIATOR() || slot < JOIN_MAX_COMMIT_SLOT) {
        // not late and definitely still in collect phase
        //merge flags
        int i;
        for (i = 0; i < FLAGS_LEN(join_data_rx->node_count); i++) {
          delta |= (join_tx->flags[i] != join_rx->flags[i]);
          join_tx->flags[i] |= join_rx->flags[i];
          flag_sum += join_tx->flags[i];
        }

        //check if remote and local knowledge differ -> if so: merge
        uint8_t delta_slots = join_data_tx->slot_count != join_data_rx->slot_count;
        if(!delta_slots){
          delta_slots = memcmp(join_data_tx->slots, join_data_rx->slots, sizeof(join_data_rx->slots)) != 0;
        }
        if ( delta_slots ) {

          // we will use +1 to detect overflows!
          node_id_t merge[sizeof(join_data_tx->slots)/sizeof(join_data_tx->slots[0])+1] = {0};

          uint8_t merge_size = join_merge_lists(merge, sizeof(merge)/sizeof(merge[0]), join_data_tx->slots, join_data_tx->slot_count, join_data_rx->slots, join_data_rx->slot_count, &delta);

          /* New overflow? */
          if (merge_size >= sizeof(join_data_tx->slots)/ sizeof(node_id_t)) {
            join_data_tx->overflow = 1;
            delta |= 1; //arrays differs, so TX
            merge_size = sizeof(join_data_tx->slots)/ sizeof(node_id_t);
          }
          join_data_tx->slot_count = merge_size;
          memcpy(join_data_tx->slots, merge, sizeof(join_data_tx->slots));
        }
      } else {
        //since half of the slots passed, we need to wait for the initiator commit.
        delta = 0;
      }
      //all flags are set?
      if( flag_sum >= FLAG_SUM(join_data_rx->node_count) && IS_INITIATOR()){
        if(!complete){
          complete_slot = slot;
        }
        //Final flood: transmit result aggressively
        complete = join_data_tx->commit;
      }
    } else if( join_data_rx->commit == 1 ){ //commit phase
      if( join_data_tx->commit == 0 ){ // we are behind
        commit_slot = slot;
        delta = 1;
        //drop local state
        memcpy(join_tx, join_rx, sizeof(join_t));
        chaos_node_count = join_data_rx->node_count;

        //get the index
        if( !chaos_has_node_index ){
          int i;
          for (i = 0; i < join_data_rx->slot_count; i++) {
            if (join_data_rx->slots[i] == node_id) {
              chaos_node_index = join_data_rx->indices[i];
              chaos_has_node_index = 1;
              LEDS_ON(LEDS_RED);
              break;
            }
          }
        }

        //set the commit flag
        if( chaos_has_node_index ){
          unsigned int array_index = chaos_node_index / 8;
          unsigned int array_offset = chaos_node_index % 8;
          join_tx->flags[array_index] |= 1 << (array_offset);
        } else {
          join_data_tx->overflow = 1;
        }
      } else {
        //merge flags
        int i;
        for (i = 0; i < FLAGS_LEN(join_data_rx->node_count); i++) {
          delta |= (join_tx->flags[i] != join_rx->flags[i]);
          join_tx->flags[i] |= join_rx->flags[i];
          flag_sum += join_tx->flags[i];
        }
      }
      //all flags are set?
      if( flag_sum >= FLAG_SUM(join_data_rx->node_count) ){
        //Final flood: transmit result aggressively
        LEDS_OFF(LEDS_RED);
        if(!complete){
          complete_slot = slot;
        }
        complete = 1;
      }
    } else if( join_data_tx->commit == 1 ){ //join_data_rx->commit == 0 --> neighbor is behind
      delta = 1;
    }
  }

  if( delta ){
    delta_at_slot = slot;
  }


  /* decide next chaos state */
  chaos_state_t next_state = CHAOS_RX;
  if ( IS_INITIATOR() && current_state == CHAOS_INIT ){
    next_state = CHAOS_TX; //for the first tx of the initiator: no increase of tx_count here
    tx_timeout_enabled = 1;

  } else if( IS_INITIATOR() && join_data_tx->commit == 0 &&
      ( (!delta && slot == delta_at_slot + COMMIT_THRESHOLD) /* no delta for some time */
          || join_data_tx->slot_count == NODE_LIST_LEN /* join list is full */
          || (slot >= JOIN_MAX_COMMIT_SLOT /* time to switch to commit phase */
              &&  join_data_tx->slot_count > 0  /* someone is joining */)
          //|| complete
      )){ //commit?
    if(join_data_tx->slot_count > 0){
      complete = 0;
    }
    commit(join_tx);
    commit_slot = slot;
    next_state = CHAOS_TX;
    invalid_rx_count = 0;
  } else if( current_state == CHAOS_RX ){
    if( chaos_txrx_success ){

      invalid_rx_count = 0;
      next_state = ( delta || complete || !tx_timeout_enabled /*first rx -> forward*/) ? CHAOS_TX : CHAOS_RX;
      tx_timeout_enabled = 1;
      if( delta ){
        tx_count_complete = 0; //restart final flood if a neighbor is behind
      }
      if ( complete ){
        if( next_state == CHAOS_TX ){
          tx_count_complete++;
        }
      }
    } else {
      invalid_rx_count++;
      if( tx_timeout_enabled ){
        unsigned short threshold = chaos_random_generator_fast() % (CHAOS_RESTART_MAX - CHAOS_RESTART_MIN) + CHAOS_RESTART_MIN;
        if( invalid_rx_count > threshold ){
          next_state = CHAOS_TX;
          invalid_rx_count = 0;
          if( complete ){
            tx_count_complete++;
          }
        }
      }
    }
  } else if( current_state == CHAOS_TX ){
    if ( complete && tx_count_complete >= N_TX_COMPLETE ){
      next_state = CHAOS_OFF;
    }
  }

  *app_flags = ( current_state == CHAOS_TX || current_state == CHAOS_INIT ) ?
      join_tx->flags : join_rx->flags;

#if 0*FAULTY_NODE_ID
  //check join_tx
  for(i=0; i<join_data_tx->slot_count && !join_debug_var.slot && rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] != 1; i++){
    if(join_data_tx->slots[i] > FAULTY_NODE_ID ||  join_data_tx->node_count > FAULTY_NODE_COUNT){
      rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1]=6;
      memcpy(rx_pkt_crc_err, tx_payload, payload_length);
      join_debug_var.debug_pos=__LINE__;
      join_debug_var.info |= TX_ERR_AFTER;
      break;
    }
  }
  //check dummy for zeros
  for(i=0; i<(RADIO_MAX_PACKET_LEN + 3)/ 4
          && !join_debug_var.slot && rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] != 1;
      i++){
    if(dummy_packet_32t[i] != 0){
      rx_pkt_crc_err[sizeof(rx_pkt_crc_err)-1] |= 0x80;
      join_debug_var.debug_pos=__LINE__;
      join_debug_var.info |= DUMMY_ERR_AFTER;
      break;
    }
  }

  if(!join_debug_var.slot && join_debug_var.info){
    join_debug_var.slot=slot;
    join_debug_var.slot_status=chaos_txrx_success;
    join_debug_var.slot_type=current_state;
  }
#endif
  int end = (slot >= JOIN_ROUND_MAX_SLOTS - 2) || (next_state == CHAOS_OFF);
  if(IS_INITIATOR() && end){
    //sort joined_nodes_map to speed up search (to enable the use of binary search) when adding new nodes
    join_reset_nodes_map();
    join_init_free_slots();
  }
  if(end){
    off_slot = slot;
  }
#if JOIN_LOG_FLAGS
  chaos_join_commit_log[slot]=join_data_tx->commit_field;
  //if(current_state == CHAOS_RX && chaos_txrx_success)
  {
    uint8_t i;
    //chaos_join_flags_log[slot]=0;
    for(i=0; i<FLAGS_LEN(join_data_tx->node_count); i++){
      chaos_join_flags_log[slot] += bit_count(join_tx->flags[i]);
    }
  }
#endif /* CHAOS_LOG_FLAGS */
  return next_state;
}

static int get_flags_length(){
  return FLAGS_LEN(MAX_NODE_COUNT);
}

static int is_pending( const uint16_t round_count ){
  //TODO: optimiziation, enable this after testing and bug fixing
  if( round_count < JOIN_ROUNDS_AFTER_BOOTUP )
  {
    pending = 1;
  }
  return pending;
  //return 1;
}

int join_last_round_is_complete( void ){
  return complete_slot;
}

uint16_t join_get_off_slot(){
  return off_slot;
}

int join_is_in_round( void ){
  return is_join_round;
}

uint16_t join_get_commit_slot(){
  return commit_slot;
}

uint8_t join_get_slot_count_from_payload( void* payload ){
  return ((join_t*)payload)->data.slot_count;
}

uint8_t join_is_committed_from_payload( void* payload ){
  return ((join_t*)payload)->data.commit;
}

static void round_begin( const uint16_t round_number, const uint8_t app_id ){
#if FAULTY_NODE_ID
  memset(&join_debug_var, 0, sizeof(join_debug_var));
  memset(rx_pkt_crc_err, 0, sizeof(rx_pkt_crc_err));
//  memset(rx_pkt_copy, 0, sizeof(rx_pkt_copy));
#endif

  commit_slot = 0;
  off_slot = JOIN_ROUND_MAX_SLOTS;
  complete_slot = 0;
  complete = 0;
  tx_count_complete = 0;
  delta_at_slot = 0;
  tx_timeout_enabled = 0;
  pending = 0;
  is_join_round = 1;
  join_t join_data;
  memset(&join_data, 0, sizeof(join_t));
#if JOIN_LOG_FLAGS
  memset(&chaos_join_flags_log, 0, sizeof(chaos_join_flags_log));
  memset(&chaos_join_commit_log, 0, sizeof(chaos_join_commit_log));
#endif

  if( IS_INITIATOR() ){
    join_data.data.node_count = chaos_node_count;
    unsigned int array_index = chaos_node_index / 8;
    unsigned int array_offset = chaos_node_index % 8;
    join_data.flags[array_index] |= 1 << (array_offset);
  } else if( !chaos_has_node_index ){
    join_data.data.slots[0] = node_id;
    join_data.data.slot_count = 1;
  } else {
    unsigned int array_index = chaos_node_index / 8;
    unsigned int array_offset = chaos_node_index % 8;
    join_data.flags[array_index] |= 1 << (array_offset);
  }
  chaos_round(round_number, app_id, (const uint8_t const*)&join_data, sizeof(join_data), JOIN_SLOT_LEN_DCO, JOIN_ROUND_MAX_SLOTS, get_flags_length(), process);
}

static void round_begin_sniffer(chaos_header_t* header){
  header->join = !chaos_has_node_index /*&& !is_join_round*/;
  if( IS_INITIATOR() ){
    header->join |= pending /*&& !is_join_round*/;
  }
}

static void round_end_sniffer(const chaos_header_t* header){
  pending |= IS_INITIATOR() && ( header->join || chaos_node_count < 2);
  is_join_round = 0;
  //TODO remove me later
#if JOIN_STRESS_TEST
#warning "JOIN_STRESS_TEST"
  if( header->round_number % JOIN_TEST_LEAVE_THRESHOLD == 0 ){
    //drop slots -> leave ->rejoin
    join_init();
    pending=IS_INITIATOR();
  }
#endif
}


////sort functions
/* Merge sort code adopted from: http://algs4.cs.princeton.edu/lectures/22Mergesort.pdf */
static void join_merge(join_node_map_entry_t a[], join_node_map_entry_t aux[], int lo, int mid, int hi) {
  int i = lo, j = mid + 1, k;
  for (k = lo; k <= hi; k++) {
    if (i > mid) {
      aux[k].node_id = a[j].node_id;
      aux[k].chaos_index = a[j].chaos_index;
      j++;
    } else if (j > hi) {
      aux[k].node_id = a[i].node_id;
      aux[k].chaos_index = a[i].chaos_index;
      i++;
    } else if (a[j].node_id <= a[i].node_id) {
      aux[k].node_id = a[j].node_id;
      aux[k].chaos_index = a[j].chaos_index;
      j++;
    } else {
      aux[k].node_id = a[i].node_id;
      aux[k].chaos_index = a[i].chaos_index;
      i++;
    }
  }
}

//recursive version: needs pre-copy in aux, but faster
static void join_merge_sort(join_node_map_entry_t a[], join_node_map_entry_t aux[], int lo, int hi)
{
  if (hi <= lo) return;
  int mid = lo + (hi - lo) / 2;
  join_merge_sort(aux, a, lo, mid);
  join_merge_sort(aux, a, mid+1, hi);
  join_merge(aux, a, lo, mid, hi);
}

//binary search
//http://www.programmingsimplified.com/c/source-code/c-program-binary-search

int join_binary_search( join_node_map_entry_t array[], int size, node_id_t search_id ){
  int first = 0;
  int last = size - 1;
  int middle = ( first + last ) / 2;

  while( first <= last ){
    if( array[middle].node_id < search_id ){
      first = middle + 1;
    } else if( array[middle].node_id == search_id ){
      return middle;
    } else {
      last = middle - 1;
    }
    middle = (first + last)/2;
  }
  return -1;
}

static int join_binary_search_chaos_index( join_node_map_entry_t array[], int size, node_id_t search_id ){

  int found = join_binary_search(array, size, search_id);

  if (found >= 0) {
    return array[found].chaos_index;
  } else {
    return found;
  }
}


int join_get_index_for_node_id(node_id_t node_id) {
  return join_binary_search_chaos_index(joined_nodes_map, chaos_node_count, node_id);
}