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
 *         Merge commit library
 * \author
 *         Beshr Al Nahas <beshr@chalmers.se>
 *         Olaf Landsiedel <olafl@chalmers.se>
 *         Valentin Poirot <poirotv@chalmers.se>
 *         Patrick Rathje <mail@patrickrathje.de>
 */

#include "contiki.h"
#include <string.h>

#include "chaos.h"
#include "chaos-random-generator.h"
#include "node.h"
#include "merge-commit.h"
#include "chaos-config.h"

#ifndef FAILURES_RATE
#define FAILURES_RATE 0
#endif

#undef ENABLE_COOJA_DEBUG
#define ENABLE_COOJA_DEBUG COOJA
#include "dev/cooja-debug.h"

/* Continue final flood until we receive a full packet? */
#define RELIABLE_FF 1

#ifndef N_TX_COMPLETE
#define N_TX_COMPLETE 9
#endif

#ifndef CHAOS_RESTART_MIN
#define CHAOS_RESTART_MIN 6
#endif

#ifndef CHAOS_RESTART_MAX
#define CHAOS_RESTART_MAX 10
#endif

#define FLAGS_LEN_X(X)   (((X) >> 3) + (((X) & 7) ? 1 : 0))
#define FLAGS_LEN   (FLAGS_LEN_X(chaos_node_count))
#define LAST_FLAGS  ((1 << (((chaos_node_count - 1) & 7) + 1)) - 1)
#define FLAG_SUM    (((FLAGS_LEN - 1) << 8) - (FLAGS_LEN - 1) + LAST_FLAGS)

#if NETSTACK_CONF_WITH_CHAOS_NODE_DYNAMIC
#define FLAGS_ESTIMATE FLAGS_LEN_X(MAX_NODE_COUNT)
#else
#define FLAGS_ESTIMATE FLAGS_LEN_X(CHAOS_NODES)
#endif

#define ARR_INDEX ((chaos_node_index)/8)
#define ARR_OFFSET ((chaos_node_index)%8)


typedef struct __attribute__((packed)) {
  merge_commit_t mc;
  uint8_t flags[FLAGS_ESTIMATE]; //maximum of 50 * 8 nodes
} merge_commit_local_t;



extern int merge_commit_merge_callback(merge_commit_t* rx_mc, merge_commit_t* tx_mc);


// Enable me if maximum is wanted
#if 0
int merge_commit_merge_callback(merge_commit_t* rx_mc, merge_commit_t* tx_mc) {
  int ret = memcmp(&rx_mc->value, &tx_mc->value, sizeof(merge_commit_value_t));

  if (ret < 0) {
    memcpy(&rx_mc->value, &tx_mc->value, sizeof(merge_commit_value_t));
    return 1;
  } else if (ret > 0) {
    memcpy(&tx_mc->value, &rx_mc->value, sizeof(merge_commit_value_t));
    return 1;
  } else {
    return 0; // already equal
  }
}
#endif

static int tx = 0, did_tx = 0;
static int complete = 0, off_slot, completion_slot, rx_progress = 0;
static int tx_count_complete = 0;
static int invalid_rx_count = 0;
static int got_valid_rx = 0;
static unsigned short restart_threshold;
static merge_commit_local_t mc_local; /* used only for house keeping and reporting */
static uint8_t* tx_flags_final = 0;

int merge_commit_get_flags_length() {
  return FLAGS_LEN;
}

static inline uint8_t* merge_commit_get_flags(merge_commit_t* mc) {
  return mc->flags;
}

static chaos_state_t
process(uint16_t round_count, uint16_t slot_count, chaos_state_t current_state, int chaos_txrx_success, size_t payload_length, uint8_t* rx_payload, uint8_t* tx_payload, uint8_t** app_flags)
{
  merge_commit_t* tx_mc = (merge_commit_t*)tx_payload;
  merge_commit_t* rx_mc = (merge_commit_t*)rx_payload;

  chaos_state_t next_state = CHAOS_RX;

  /* the application reports a packet coming from the initiator, so we can synchronize on it;
  * e.g., we got a phase transition that only the initiator can issue */
  int request_sync = 0;

  if( IS_INITIATOR() && current_state == CHAOS_INIT ){
    next_state = CHAOS_TX; //for the first tx of the initiator: no increase of tx_count here
    got_valid_rx = 1;      //to enable retransmissions
  }
  else if (current_state == CHAOS_RX) {

    // check if the transmission was successful
    if (chaos_txrx_success) {
      got_valid_rx = 1;
      tx = 0;

      //be careful: do not mix the different phases
      if (tx_mc->phase == rx_mc->phase) {
        // same phase

        // calculate sum of flags

        uint16_t flag_sum = 0;
        uint16_t rx_flag_sum = 0;
        int i;
        uint8_t* tx_flags = merge_commit_get_flags(tx_mc);
        uint8_t* rx_flags = merge_commit_get_flags(rx_mc);

        //merge and tx if flags differ
        for( i = 0; i < FLAGS_LEN; i++){
          tx |= (rx_flags[i] != tx_flags[i]);
          tx_flags[i] |= rx_flags[i];
          flag_sum += tx_flags[i];
          rx_flag_sum += rx_flags[i];
        }

        if (tx_mc->phase == PHASE_MERGE) {


          tx |= merge_commit_merge_callback(rx_mc, tx_mc);

          // Check if we should do the next phase!
          if (IS_INITIATOR() && flag_sum == FLAG_SUM) {
            LEDS_ON(LEDS_RED);
            memset(tx_flags, 0, merge_commit_get_flags_length());
            tx_flags[ARR_INDEX] |= 1 << (ARR_OFFSET);

            // Next phase \o/
            tx_mc->phase = PHASE_COMMIT;
            //TODO: we could also abort here

            tx = 1;
            leds_on(LEDS_GREEN);
          }

        } else if (tx_mc->phase == PHASE_COMMIT) {
          if (flag_sum == FLAG_SUM) {
            tx = 1;
            if(!complete){
              completion_slot = slot_count;
            }
            complete = 1;
            rx_progress |= (rx_flag_sum == FLAG_SUM); /* received a complete packet */
          }
        }

      } else if (tx_mc->phase < rx_mc->phase) {
        // received phase is more advanced than local one -> switch to received state (and set own flags)
        memcpy(tx_mc, rx_mc, sizeof(merge_commit_t) + merge_commit_get_flags_length());
        uint8_t* tx_flags = merge_commit_get_flags(tx_mc);
        tx_flags[ARR_INDEX] |= 1 << (ARR_OFFSET);
        tx = 1;
        leds_on(LEDS_BLUE);
        request_sync = 1;
      } else {//tx_mc_pc->phase > rx_mc_pc->phase
        //local phase is more advanced. Drop received one and just transmit to allow others to catch up
        tx = 1;
      }

      if( tx ){
        next_state = CHAOS_TX;
        if( complete ){
          tx_count_complete++;
        }
      }

    } else if(got_valid_rx) {
      invalid_rx_count++;
      if(invalid_rx_count > restart_threshold){
        next_state = CHAOS_TX;
        invalid_rx_count = 0;
        if( complete ){
          tx_count_complete++;
        }
        restart_threshold = chaos_random_generator_fast() % (CHAOS_RESTART_MAX - CHAOS_RESTART_MIN) + CHAOS_RESTART_MIN;
      }
    }

  } else if(current_state == CHAOS_TX && (rx_progress || !RELIABLE_FF) && tx_count_complete >= N_TX_COMPLETE){
    next_state = CHAOS_OFF;
    leds_off(LEDS_GREEN);
  }

  int end = (slot_count >= MERGE_COMMIT_ROUND_MAX_SLOTS - 1) || (next_state == CHAOS_OFF);
  if(end){
    memcpy(&mc_local.mc.value, &tx_mc->value, sizeof(merge_commit_value_t));
    mc_local.mc.phase = tx_mc->phase;
    tx_flags_final = tx_mc->flags;

    off_slot = slot_count;
  }

  if( next_state == CHAOS_TX ){
    did_tx = 1;
  }

  if( request_sync ){
    if( next_state == CHAOS_TX ){
      next_state = CHAOS_TX_SYNC;
    } else if( next_state == CHAOS_RX ){
      next_state = CHAOS_RX_SYNC;
    }
  }

  return next_state;
}

int merge_commit_is_pending(const uint16_t round_count){
  return 1;
}

int merge_commit_did_tx(){
  return did_tx;
}

uint16_t merge_commit_get_off_slot(){
  return off_slot;
}

int merge_commit_round_begin(const uint16_t round_number, const uint8_t app_id, merge_commit_value_t* merge_commit_value, uint8_t* phase, uint8_t** final_flags)
{
  tx = 0;
  did_tx = 0;
  got_valid_rx = 0;
  complete = 0;
  tx_count_complete = 0;
  invalid_rx_count = 0;
  off_slot = MERGE_COMMIT_ROUND_MAX_SLOTS;
  completion_slot = 0;
  tx_flags_final = 0;
  rx_progress = 0;

  /* init random restart threshold */
  restart_threshold = chaos_random_generator_fast() % (CHAOS_RESTART_MAX - CHAOS_RESTART_MIN) + CHAOS_RESTART_MIN;

  memset(&mc_local, 0, sizeof(mc_local));
  memcpy(&mc_local.mc.value, merge_commit_value, sizeof(merge_commit_value_t));
  mc_local.mc.phase = PHASE_MERGE;
  /* set my flag */
  uint8_t* flags = merge_commit_get_flags(&mc_local.mc);
  flags[ARR_INDEX] |= 1 << (ARR_OFFSET);

  chaos_round(round_number, app_id, (const uint8_t const*)&mc_local, sizeof(mc_local.mc) + merge_commit_get_flags_length(), MERGE_COMMIT_SLOT_LEN_DCO, MERGE_COMMIT_ROUND_MAX_SLOTS, merge_commit_get_flags_length(), process);
  memcpy(mc_local.mc.flags, tx_flags_final, merge_commit_get_flags_length());
  memcpy(merge_commit_value, &mc_local.mc.value, sizeof(merge_commit_value_t));
  *final_flags = mc_local.flags;
  *phase = mc_local.mc.phase;
  return completion_slot;
}

