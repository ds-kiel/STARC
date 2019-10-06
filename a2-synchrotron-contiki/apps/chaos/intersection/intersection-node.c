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
/**
 * \file
 *         A2-Synchrotron two-phase commit application for intersections
 * \author
 *         Beshr Al Nahas <beshr@chalmers.se>
 *         Olaf Landsiedel <olafl@chalmers.se>
 *         Patrick Rathje <mail@patrickrathje.de>
 */

#include "contiki.h"
#include <stdio.h> /* For printf() */
#include "net/netstack.h"
#include "dev/serial-line.h"
#include <stdlib.h>

#include "chaos-control.h"
#include "node.h"


#include "merge-commit.h"
#include "random.h"


uint8_t wanted_channel;

int decode_data( char * dest, const char * source) {

  int initial_size = strlen(source);
  int res_size = 0;
  int i = 0;

  for(i = 0; i < initial_size; ++i) {

    char c = source[i];

    if ((uint8_t)c == 0x11) {
      i++;
      if (i < initial_size) {
        c = source[i];

        if ((uint8_t) c == 0x11) {
          // Nothing to do
        } else if ((uint8_t) c == 0x12) {
          c = '\n';
        } else if ((uint8_t) c == 0x13) {
          c = '\r';
        } else if ((uint8_t) c == 0x14) {
          c = '\0';
        }

        dest[res_size] = c;
        res_size++;
      }
    } else {
      // no need to convert anything
      dest[res_size] = c;
      res_size++;
    }
  }
  return res_size;
}

void send_data_part(char * data, uint32_t size) {
  int i = 0;
  for(i = 0; i < size; ++i) {
    // we will encode the special characters \n, \r and zero like the following
    // \n -> 0x11 0x12
    // \r -> 0x11 0x13
    // 0 -> 0x11 0x14
    // 0x11 -> 0x11 0x11 This is needed so that we know

    char c = data[i];
    if (c == '\n') {
      putchar(0x11);
      putchar(0x12);
    } else if (c == '\r') {
      putchar(0x11);
      putchar(0x13);
    }else if (c == '\0') {
      putchar(0x11);
      putchar(0x14);
    } else if ((uint8_t) c == 0x11){
      putchar(0x11);
      putchar(0x11);
    } else {
      putchar(c);
    }
  }
}

void send_data( char * data, uint32_t size) {
  // we will use device control characters to encode our message!
  printf("#VANET ");
  send_data_part(data, size);
  putchar('\n'); //aaaand finish the newline ;)
}

void send_str(const char *str) {
  printf("#VANET %s\n", str);
}


typedef struct __attribute__((packed)) {
    uint8_t size;
    uint8_t tiles[NUM_TILES];
} path_t;


static int pos_to_id(int x, int y) {
  return y*TILES_WIDTH+x;
}

static void id_to_pos(int *x, int *y, int id) {
  *x = id%TILES_WIDTH;
  *y = id/TILES_WIDTH;
}

static void reserve_path_with_offset(merge_commit_value_t *val, path_t* path, int node_id, int offset) {
  int i;
  for(i = offset; i < path->size; ++i) {
    uint8_t *tile_res = &val->tile_reservations[path->tiles[i]];
    if (*tile_res == 0) {
      *tile_res = node_id;
    }
  }
}

static void reserve_path(merge_commit_value_t *val, path_t* path, int node_id) {
  reserve_path_with_offset(val, path, node_id, 0);
}

static int path_is_reserved(merge_commit_value_t* plan, path_t *path, int node_id) {

  if (path->size == 0) {
    return 0;
  }

  int i = 0;

  for(i = 0; i < path->size; ++i) {
    int x = plan->tile_reservations[path->tiles[i]];
    if (x != node_id) {
      return 0; // Mismatch
    }
  }

  return 1;
}

static int path_available(merge_commit_value_t *plan, path_t* path, int node_id) {
  int i;
  for(i = 0; i < path->size; ++i) {
    int x = plan->tile_reservations[path->tiles[i]];
    if (x != 0 && x != node_id) {
      return 0;
    }
  }
  return 1;
}


static uint16_t own_priority = 0;


static path_t own_reservation;
static int target_x = 0;
static int target_y = 0;

void print_tiles(merge_commit_value_t *val) {
  int x = 0;
  int y = 0;

  for(y = 0; y < TILES_HEIGHT; y++) {
    for(x = 0; x < TILES_WIDTH; x++) {
      printf("%x", val->tile_reservations[y*TILES_WIDTH+x]);
    }
    printf(", ");
  }
  printf("\n");
}

static void mc_round_begin(const uint16_t round_count, const uint8_t id);


CHAOS_APP(chaos_merge_commit_app, MERGE_COMMIT_SLOT_LEN, MERGE_COMMIT_ROUND_MAX_SLOTS, 0, merge_commit_is_pending, mc_round_begin);
CHAOS_APPS(&chaos_merge_commit_app);

/* Commit variables */
static merge_commit_value_t mc_value;
static merge_commit_value_t mc_commited_value;
static merge_commit_value_t mc_last_commited_value;
static uint8_t* mc_flags;
static uint8_t  mc_complete = 0, mc_phase = 0, mc_type = 0;
static uint16_t mc_off_slot;
static uint16_t mc_round_count_local = 0;

static uint16_t arrival_round = 0;

static void update_reservation() {
  // Reset everything!
  memset(&mc_value, 0, sizeof(merge_commit_value_t));

  // And if we have a current reservation, lets use it!

  
  if (chaos_has_node_index && own_reservation.size > 0) {
    if (!WAIT_FOR_FREE_PATH || path_available(&mc_last_commited_value, &own_reservation, chaos_node_index+1)) {
      reserve_path(&mc_value, &own_reservation, chaos_node_index+1);

      // initialized default value for the priority if none is set
      if (!own_priority) {
        own_priority = 0xFFFF-arrival_round; // 0 is no reservation, 0xFFFF is for the ones in the intersection
        if (own_priority == 0xFFFF) {
          own_priority = 0xFFFF-1;
        }
        if (own_priority == 0) {
          own_priority = 1;
        }
      }

      mc_value.priorities[chaos_node_index] = own_priority;
    }
  }
}

PROCESS(mc_process, "Merge-Commit process");
PROCESS_THREAD(mc_process, ev, data)
{
  // TODO: init commit value
PROCESS_BEGIN();
  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(ev == PROCESS_EVENT_POLL);

#if MERGE_COMMIT_ADVANCED_STATS

const uint8_t slots_per_msg = 50;
  // Start Message
  printf("#VANET MC-STATS-START");


  uint8_t tmp = 0;

  // we first send the round number
  tmp = (mc_round_count_local >> 8)&0xFF;
  send_data_part((char*)&tmp, 1);
  tmp = mc_round_count_local&0xFF;
  send_data_part((char*)&tmp, 1);

  uint16_t remaining_slots = mc_off_slot+1;

  // and the number of slots
  tmp = (remaining_slots >> 8)&0xFF;
  send_data_part((char*)&tmp, 1);
  tmp = remaining_slots&0xFF;
  send_data_part((char*)&tmp, 1);

  // and finally the number of slots per message
  send_data_part((char*)&slots_per_msg, 1);
  putchar('\n'); // and finish with newline ;)

  uint8_t cur_msg = 0;

  uint8_t i;
  while(remaining_slots > 0) {
    uint8_t num_slots = MIN(slots_per_msg, remaining_slots);
    printf("#VANET MC-STATS-SLOTS");
    send_data_part(&merge_commit_advanced_stats[cur_msg*slots_per_msg], sizeof(merge_commit_advanced_slot_stats_t)*MIN(slots_per_msg, remaining_slots));
    printf("\n");
    cur_msg++;
    remaining_slots -= num_slots;
  }
  printf("#VANET MC-STATS-END\n");
#endif

  arrival_round = mc_round_count_local;

  uint8_t completed = mc_phase == PHASE_COMMIT || (IS_INITIATOR() && chaos_node_count <= 1);

  if (merge_commit_has_left()) {
    send_str("left");
  } else if (merge_commit_has_joined()) {
    // this could also be a rejoin, that is why it is checked here and not in the completed commit
    printf("#VANET joined");
    uint8_t tmp = (chaos_node_index)&0xFF;
    send_data_part((char*)&tmp, 1);
    putchar('\n');
  }

  if (mc_type == TYPE_COORDINATION) {
    if (completed) {
      if(chaos_has_node_index){
        printf("Commit completed (Coordination)\n");
        printf("OFFSLOT: %d\n", mc_off_slot);

        printf("own reserv.size %d\n", own_reservation.size);

        //print_tiles(&mc_commited_value);

        // Set latest known commit value
        memcpy(&mc_last_commited_value, &mc_commited_value, sizeof(merge_commit_value_t));

        if (path_is_reserved(&mc_commited_value, &own_reservation, chaos_node_index+1)) {
          own_priority = 0xFFFF; // we do not want that any other node intercepts our request... Priority is copied later
          send_str("accepted"); // ack the new reservation
          printf("Node id %d was accepted\n", node_id);
        }
      } else {
        // we are not part of the network
        printf("{rd %u res} mc: waiting to join, n: %u\n", mc_round_count_local, chaos_node_count);
      }
    } else {
        printf("Commit NOT completed (Coordination)\n");
    }
  } else {
    if (completed) {
      printf("Commit completed (Election)\n");
      printf("OFFSLOT: %d\n", mc_off_slot);
    } else {
      printf("Commit NOT completed (Election)\n");
    }
  }

  // We reset the value every round
  // To prevent misbehavior on rejoins etc.
  update_reservation();

  // Notify vehicle about our initiator status, to coordinate the network creation
  if (IS_INITIATOR()) {
    send_str("is_initiator");
  }
  // Notify round end to simulation, to be able to remove vehicles...
    send_str("round_end");
  }
PROCESS_END();
}


PROCESS(comm_process, "communication process");
/*
 * This process reacts to messages from the serial port
 */
PROCESS_THREAD(comm_process, ev, data)
{
  PROCESS_BEGIN();

  static char comm_buf[SERIAL_LINE_CONF_BUFSIZE];
  memset(&mc_last_commited_value, 0, sizeof(merge_commit_value_t));

  send_str("init"); // send init to plugin

  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(ev == serial_line_event_message && data != NULL);

    int size = decode_data(comm_buf, (const char *) data);

    if (size == 0) {
      continue;
    }

    char msg_id = comm_buf[0];
    int msg_size = size-1;
    char * msg_data = comm_buf+1;

    printf("decoded %d\n with id %c\n", msg_size, msg_id);

    if (msg_id == 'I') {
      // set as initiator -> create a new network
      chaos_start = 1;
      chaos_set_is_initiator(1);
      // and init the network ;)
      join_init();
      printf("#VANET joined");
      uint8_t tmp = (chaos_node_index)&0xFF;
      send_data_part((char*)&tmp, 1);
      putchar('\n');
    } else if (msg_id == 'J') {
      chaos_start = 1;
      merge_commit_wanted_join_state = MERGE_COMMIT_WANTED_JOIN_STATE_JOIN;
      printf("Trying to join network\n");
    } else if(msg_id == 'L') {
      merge_commit_wanted_join_state = MERGE_COMMIT_WANTED_JOIN_STATE_LEAVE;
      printf("Trying to leave network\n");
    } else if(msg_id == 'C' && msg_size == 1) {
      wanted_channel = msg_data[0];
      printf("Trying to change channel to %d\n", wanted_channel);
      if (!get_round_synced()) {
        // we directly change the channel
        chaos_multichannel_set_current_channel(wanted_channel);
      }
    } else if (msg_id == 'R') {
      // copy that reservation to our own

      own_reservation.size = msg_size;
      memcpy(own_reservation.tiles, msg_data, msg_size);

      uint8_t part_of_current = path_is_reserved(&mc_value, &own_reservation, chaos_node_index+1);

      if (part_of_current) {
        printf("Got updated reservation with size %d: \n", msg_size);
      } else {
        printf("Got NEW reservation with size %d: \n", msg_size);
      }
      int i = 0;
      for(i = 0; i < msg_size; ++i) {
        int x;
        int y;
        id_to_pos(&x, &y, ((const char *)msg_data)[i]);
        printf("%d, ", ((const char *)msg_data)[i]);
      }
      printf("\n");

      // now we check if the new reservation is already part of our current reservation
      // we distinguish three cases
      // 1. the reservation is just 0
      // 2. the reservation is part of the current reservation
      // 3. the reservation is completely new

      if (msg_size == 0 || !part_of_current) {
        own_priority = 0; // reset own priority
      } else {
        // we keep the current priority
      }
      update_reservation(); // update the reservation to use it in the next round already ;)
      send_str("ack"); // ack the new reservation
    }
  };

  PROCESS_END();
}

unsigned short rseed = 0; /* Will be set to a value by cooja! */
PROCESS(main_process, "Main intersection process");
AUTOSTART_PROCESSES(&main_process);
PROCESS_THREAD(main_process, ev, data)
{
  PROCESS_BEGIN();
  NETSTACK_MAC.on();

  if (rseed == 0) {
    rseed = node_id;
  }
  random_init(rseed);
  printf("Starting main process with seed: %d\n", rseed);

#if CHAOS_AUTOMATIC_START
#else
  chaos_start = 0;
#endif

  // we initialize the chaos channel here!

  merge_commit_wanted_type = TYPE_COORDINATION; // all want to do normal coordination

  process_start(&comm_process, NULL);
  process_start(&mc_process, NULL);
  PROCESS_WAIT_EVENT_UNTIL(ev == PROCESS_EVENT_POLL);
  PROCESS_END();
}

static void mc_round_begin(const uint16_t round_count, const uint8_t id){
  memcpy(&mc_commited_value, &mc_value, sizeof(merge_commit_value_t));

  if (wanted_channel != chaos_multichannel_get_current_channel()) {
    chaos_multichannel_set_current_channel(wanted_channel);
  }

  // We need to check that leaving nodes will never be elected in case of present non-leaving nodes
  if (merge_commit_wanted_join_state == MERGE_COMMIT_WANTED_JOIN_STATE_LEAVE) {
    merge_commit_wanted_election_priority = 0; // Zero as election priority
  } else {
    merge_commit_wanted_election_priority = 0xFFFF-own_priority;
    // we will add the length of our path, so the one with the longest path is going to be elected
    // need to take care of overflows here ;)
    if (merge_commit_wanted_election_priority + own_reservation.size < merge_commit_wanted_election_priority) {
      merge_commit_wanted_election_priority = 0xFFFF;
    } else {
      merge_commit_wanted_election_priority += own_reservation.size;
    }
    if (merge_commit_wanted_election_priority == 0) {
      merge_commit_wanted_election_priority = 1; // Make sure that non-leaving nodes will be elected
    }
  }

  mc_complete = merge_commit_round_begin(round_count, id, &mc_commited_value, &mc_phase, &mc_type, &mc_flags);
  mc_off_slot = merge_commit_get_off_slot();
  mc_round_count_local = round_count;
  process_poll(&mc_process);
}


void merge_commit_merge_callback(merge_commit_t* rx_mc, merge_commit_t* tx_mc) {

 /* static int time_diff = 0;
  int start = RTIMER_NOW(); */

  static int i = 0;
  merge_commit_value_t new;
  memset(&new, 0, sizeof(merge_commit_value_t));

  // cache the reservations for faster access!
  // add the zero, which is no reservation
  uint16_t reservation_priorities[MAX_NODE_COUNT+1];

  // the first one has no priority at all
  reservation_priorities[0] = 0;

  for(i = 0; i < MAX_NODE_COUNT; ++i) {
    // we can use bitwise or here since either one of them is 0 or both have the same value...
    uint16_t merged_priority = rx_mc->value.priorities[i] | tx_mc->value.priorities[i];

    // save the merged priority in the merged commit value
    new.priorities[i] = merged_priority;
    reservation_priorities[i+1] = merged_priority;
  }

  merge_commit_value_t *rv = &rx_mc->value;
  merge_commit_value_t *tv = &tx_mc->value;

  for(i = 0; i < NUM_TILES; ++i) {

    // we compute the maximum of the received and our own grid
    uint16_t a = rv->tile_reservations[i];
    uint16_t b = tv->tile_reservations[i];
    uint16_t res;

    if (reservation_priorities[a] == reservation_priorities[b]) {
      // use the id as a tie-breaker, higher ids first
      if (b >= a) {
        res = b;
      } else {
        res = a;
      }
    } else if (reservation_priorities[a] > reservation_priorities[b]) {
      res = a;
    } else {
      res = b;
    }

    new.tile_reservations[i] = res;
  }
  // now copy the generated reservations
  memcpy(&tx_mc->value, &new, sizeof(merge_commit_value_t));

  /*int endTime = RTIMER_NOW();
  if (time_diff < endTime-start) {
    printf("New Merge-Commit diff %d ms\n", 1000*time_diff/RTIMER_SECOND);
    time_diff = endTime-start;
  }*/
}