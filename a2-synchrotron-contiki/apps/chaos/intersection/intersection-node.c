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
 *
 */

#include "contiki.h"
#include <stdio.h> /* For printf() */
#include "net/netstack.h"
#include <stdlib.h>

#undef INITIATOR_NODE
#define INITIATOR_NODE 1

#include "chaos-control.h"
#include "node.h"


#define EXCLUDE_MERGE_COMMIT_VALUE_STRUCT 1
typedef struct __attribute__((packed)) {
    uint8_t tile_reservations[4];
} merge_commit_value_t;


#include "merge-commit.h"
#include "random.h"

#define REPEAT 1
#define MOVE_INTERVAL CLOCK_SECOND
#define MOVE_BREAK 10*CLOCK_SECOND


#define TILES_WIDTH 2
#define TILES_HEIGHT 2
#define NUM_TILES (TILES_WIDTH * TILES_HEIGHT)


static int px = 0;
static int py = 0;
static int dx = 0;
static int dy = 0;

static int opx = 0;
static int opy = 0;

static int accepted = 0;

static int tile_reservation_code;

unsigned short rseed = 0; /* Will be set to a value by cooja! */
static void init_pos_and_dir() {

  tile_reservation_code = 0;

  switch (node_id%4) {
    case 1:
      px = -1; py = 1;
      dx = 1; dy = 0;
      //tile_reservation_code = "0 1 1 1";
      tile_reservation_code = (1 << 2) | (1 << 1) | 1;
      break;
    case 2:
      px = 0; py = -1;
      dx = 0; dy = 1;
      //tile_reservation_code = "0 0 0 1";
      tile_reservation_code = 1;
      break;
    case 3:
      px = 2; py = 0;
      dx = -1; dy = 0;
      //tile_reservation_code = "1 0 0 0";
      tile_reservation_code = (1 << 3);
      break;
    case 0:
      px = 1; py = 2;
      dx = 0; dy = -1;
      //tile_reservation_code = "1 1 1 0";
      tile_reservation_code = (1 << 3) | (1 << 2) | (1 << 1);
      break;
  }
}

static int has_reservations(merge_commit_value_t* mc_val) {
  int i = 0;
  for (i = 0; i < NUM_TILES; ++i) {
    if (mc_val->tile_reservations[i] != 0) {
      return 1;
    }
  }
  return 0;
}

static int reservations_exists(merge_commit_value_t* mc_source, merge_commit_value_t* mc_val) {
  int i = 0;
  for (i = 0; i < NUM_TILES; ++i) {
    if (mc_val->tile_reservations[i] != 0 && mc_val->tile_reservations[i] != mc_source->tile_reservations[i]) {
      return 0; // Mismatch
    }
  }
  return 1;
}

static int check_reservation(merge_commit_value_t* mc_val, int node_id, int reservation) {
  int i1 = (reservation >> 2) & 3;
  int i2 = reservation & 3;

  if (mc_val->tile_reservations[i1] == 0 || mc_val->tile_reservations[i1] == node_id) {
    if (mc_val->tile_reservations[i2] == 0 || mc_val->tile_reservations[i2] == node_id) {
      return 1;
    }
  }
  return 0;
}

static void free_reservations(merge_commit_value_t* mc_val, int node_id) {
  int i = 0;
  for (i = 0; i < NUM_TILES; ++i) {
    if (mc_val->tile_reservations[i] == node_id) {
      mc_val->tile_reservations[i] = 0;
    }
  }
}

static void plan_reservation(merge_commit_value_t* mc_val, int node_id, int reservation) {
  free_reservations(mc_val, node_id);

  int i1 = (reservation >> 2) & 3;
  int i2 = reservation & 3;
  mc_val->tile_reservations[i1] = node_id;
  mc_val->tile_reservations[i2] = node_id;
}


static void mc_round_begin(const uint16_t round_count, const uint8_t id);


CHAOS_APP(chaos_merge_commit_app, MERGE_COMMIT_SLOT_LEN, MERGE_COMMIT_ROUND_MAX_SLOTS, 1, merge_commit_is_pending, mc_round_begin);

#if NETSTACK_CONF_WITH_CHAOS_NODE_DYNAMIC
#include "join.h"
CHAOS_APPS(&join, &chaos_merge_commit_app);
#else
CHAOS_APPS(&chaos_merge_commit_app);
#endif /* NETSTACK_CONF_WITH_CHAOS_NODE_DYNAMIC */

/* Commit variables */
static merge_commit_value_t mc_value;
static merge_commit_value_t mc_commited_value;
static uint8_t* mc_flags;
static uint8_t  mc_complete = 0, mc_phase = 0;
static uint16_t mc_off_slot;
static uint16_t mc_round_count_local = 0;


// TODO: check type of node id...



PROCESS(mc_process, "Merge-Commit process");
PROCESS_THREAD(mc_process, ev, data)
{
  // TODO: init commit value
PROCESS_BEGIN();
  while(1) {
    PROCESS_YIELD();

    printf("Commit yield\n");
    if(chaos_has_node_index){
      if (mc_phase == PHASE_COMMIT) {
        printf("Commit completed\n");
        //COMMIT
        if (has_reservations(&mc_value) && reservations_exists(&mc_commited_value, &mc_value)) {
          printf("Node id %d was accepted\n", node_id);
          accepted = 1;
        }
      }
    } else {
      printf("{rd %u res} 2pc: waiting to join, n: %u\n", mc_round_count_local, chaos_node_count);
    }
    // COMMIT HAS FINISHED!
    // CHECK IF IT WAS SUCCESSFUL!
  }
PROCESS_END();
}

PROCESS(movement_process, "movement process");

PROCESS_THREAD(movement_process, ev, data)
{

  static struct etimer timer;
  PROCESS_BEGIN();


  // first set the position based on our mode id and move to the position
  init_pos_and_dir();
  opx = px;
  opy = py;




  etimer_set(&timer, MOVE_INTERVAL);
  do {
    // Reset original position
    px = opx;
    py = opy;
    printf("#MoveTo %d.0 %d.0\n", px, py);

    memset(&mc_value, 0, sizeof(merge_commit_value_t));
    plan_reservation(&mc_value, node_id, tile_reservation_code);

    // insert extra wait_for_request time
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&timer)); etimer_reset(&timer);

    while (1) {
      if (accepted) {
        printf("#Move %d.0 %d.0\n", dx, dy);
        px += dx;
        py += dy;

        // check if we should release the reservation
        if (abs(opx-px) + abs(opy-py) > 2) {
          accepted = 0;

          // Release
          // Update collect value
          memset(&mc_value, 0, sizeof(merge_commit_value_t));
          break;
        }
      }

      PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&timer)); etimer_reset(&timer);
    }

    // some extra waiting time ;)
    static int random_waits = 0;

    etimer_restart(&timer);
    random_waits = abs((random_rand() % 10))+5;

    for(; random_waits > 0; random_waits--) {
      PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&timer)); etimer_reset(&timer);
    }

  } while(REPEAT);

  PROCESS_END();
}


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

  process_start(&movement_process, NULL);
  process_start(&mc_process, NULL);
  PROCESS_YIELD();
  PROCESS_END();
}

static void mc_round_begin(const uint16_t round_count, const uint8_t id){
  memcpy(&mc_commited_value, &mc_value, sizeof(merge_commit_value_t));
  mc_complete = merge_commit_round_begin(round_count, id, &mc_commited_value, &mc_phase, &mc_flags);
  mc_off_slot = merge_commit_get_off_slot();
  mc_round_count_local = round_count;
  process_poll(&mc_process);
}


static int reserve_if_possible(merge_commit_value_t *dest, merge_commit_value_t *src, int id) {

  // first collect the tiles!

  uint8_t tile_ids[NUM_TILES] = {0};
  uint8_t tile_count = 0;
  int i = 0;

  for(i = 0; i < NUM_TILES; ++i) {
    if (src->tile_reservations[i] == id) {
      // add to array
      tile_ids[tile_count] = i;
      tile_count++;
    }
  }

  if (tile_count > 0) {
    // node has reservation!

    // check if the reservation is possible
    for(i = 0; i < tile_count; ++i) {
      int r = dest->tile_reservations[tile_ids[i]];
      if (r != 0 &&  r != id) {
        return 0; // NOT POSSIBLE -> we can directly return
      }
    }

    // reservation is possible --> so we set all ids
    for(i = 0; i < tile_count; ++i) {
      dest->tile_reservations[tile_ids[i]] = id;
    }
    return 1; // POSSIBLE \o/
  }
  return 0;
}

void print_tiles(merge_commit_value_t *val) {
  int x = 0;
  int y = 0;

  for(y = 0; y < TILES_HEIGHT; y++) {
    for(x = 0; x < TILES_WIDTH; x++) {
      printf("%d", val->tile_reservations[y*TILES_WIDTH+x]);
    }
    printf("\n");
  }
}

int merge_commit_merge_callback(merge_commit_t* rx_mc, merge_commit_t* tx_mc) {

  int i = 0;
  int c = 0;
  merge_commit_value_t new;
  memcpy(&new, &tx_mc->value, sizeof(merge_commit_value_t));



  for(i = 1; i <= chaos_node_count; ++i) {
    if (!reserve_if_possible(&new, &rx_mc->value, i)) {
      reserve_if_possible(&new, &tx_mc->value, i);
    }
  }

  int ret = memcmp(&new, &tx_mc->value, sizeof(merge_commit_value_t)) ||
            memcmp(&new, &rx_mc->value, sizeof(merge_commit_value_t));


  // now copy the generated reservations
  memcpy(&tx_mc->value, &new, sizeof(merge_commit_value_t));
  return ret;
}