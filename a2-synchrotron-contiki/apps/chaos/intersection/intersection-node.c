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
#include "dev/serial-line.h"
#include <stdlib.h>

#undef INITIATOR_NODE
#define INITIATOR_NODE 1

#include "chaos-control.h"
#include "node.h"


#include "merge-commit.h"
#include "random.h"

#define REPEAT 1
#define MOVE_INTERVAL CLOCK_SECOND/10



// TODO: We need to really test these functions!!
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

  //TODO: test if the newline character is included in the message

  return res_size;
}

void send_data( char * data, uint32_t size) {
  // we will use device control characters to encode our message!

  printf("#VANET ");
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

// TODO: Move the tile-specific code to other files
// TODO: We should
static void extract_path(path_t* dest, merge_commit_value_t *src, int node_id) {
  int i = 0;
  dest->size = 0;

  for(i = 0; i < NUM_TILES; ++i) {
    if (src->tile_reservations[i] == node_id) {
      // add to array
      dest->tiles[dest->size] = i;
      dest->size++;
    }
  }
}
  
static void reserve_path_with_offset(merge_commit_value_t *val, path_t* path, int node_id, int offset) {
  int i;
  for(i = offset; i < path->size; ++i) {
    val->tile_reservations[path->tiles[i]] = node_id;
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


static int px = 0;
static int py = 0;
static int dx = 0;
static int dy = 0;

// original positions and directions
static int opx = 0;
static int opy = 0;
static int odx = 0;
static int ody = 0;

static int accepted = 0;

static uint16_t own_arrival = 0;


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
}


// TODO: No positions and dirs inside c
unsigned short rseed = 0; /* Will be set to a value by cooja! */
static void init_pos_and_dir() {


  int offset = (node_id-1)%3;

  switch((node_id-1)/3) {
    case 0:
      px = -1; py = 3+offset;
      odx = 1; ody = 0;
      break;
    case 1:
      px = 3+offset; py = 6;
      odx = 0; ody = -1;
      break;
    case 2:
      px = 6; py = 2-offset;
      odx = -1; ody = 0;
      break;
    case 3:
      px = 2-offset; py = -1;
      odx = 0; ody = 1;
      break;
  }
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
static merge_commit_value_t mc_last_commited_value;
static uint8_t* mc_flags;
static uint8_t  mc_complete = 0, mc_phase = 0;
static uint16_t mc_off_slot;
static uint16_t mc_round_count_local = 0;


// TODO: Someone should set the arrival value to 1 in order to keep the reservations until freed
static void set_own_arrival(merge_commit_value_t *val) {
  if (ARRIVAL_TIMES) {
    if (!own_arrival) {
      own_arrival = mc_round_count_local+2; // 0 is no reservation, 1 is for the ones in the intersection
    }
    val->arrivals[node_id-1] = own_arrival;
  }
}

PROCESS(mc_process, "Merge-Commit process");
PROCESS_THREAD(mc_process, ev, data)
{
  // TODO: init commit value
PROCESS_BEGIN();
  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(ev == PROCESS_EVENT_POLL);

    if(chaos_has_node_index){
      if (mc_phase == PHASE_COMMIT) {
        printf("Commit completed\n");
        printf("OFFSLOT: %d\n", mc_off_slot);

        // Set latest known commit value
        memcpy(&mc_last_commited_value, &mc_commited_value, sizeof(merge_commit_value_t));

        // TODO: Send commited value to JAVA

        if (path_is_reserved(&mc_commited_value, &own_reservation, node_id)) {
          own_arrival = 1; // we do not want that any other node intercepts our request...
          set_own_arrival(&mc_value); // we need to set this!
          send_str("accepted"); // ack the new reservation
          printf("Node id %d was accepted\n", node_id);
        } else if (own_reservation.size > 0 && !path_is_reserved(&mc_value, &own_reservation, node_id)){
          // TODO: At this point, we really just want to find ANY way ;)
          // Add this with a parameter
          if (WAIT_FOR_FREE_PATH && path_available(&mc_last_commited_value, &own_reservation, node_id)) {
            reserve_path(&mc_value, &own_reservation, node_id);
            set_own_arrival(&mc_value);
          }
        }
      } else {
        printf("Commit NOT completed\n");
      }
    } else {
      printf("{rd %u res} 2pc: waiting to join, n: %u\n", mc_round_count_local, chaos_node_count);
    }
    // COMMIT HAS FINISHED!
    // CHECK IF IT WAS SUCCESSFUL!
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

  // TODO: This could be reduced
  static char comm_buf[SERIAL_LINE_CONF_BUFSIZE];
  memset(&mc_last_commited_value, 0, sizeof(merge_commit_value_t));

  send_str("init"); // send init to plugin

  while(1) {
    PROCESS_WAIT_EVENT_UNTIL(ev == serial_line_event_message && data != NULL);

    int size = decode_data(comm_buf, (const char *) data);
    printf("decoded %d\n", size);

    //if (size == 0) {
    //  continue;
    //}

    // for now we only receive a new reservation...
    // copy that reservation to our own

    own_reservation.size = size;
    memcpy(own_reservation.tiles, comm_buf, size);

    uint8_t part_of_current = path_is_reserved(&mc_value, &own_reservation, node_id);

    if (part_of_current) {
      printf("Got updated reservation with size %d: ", size);
    } else {
      printf("Got NEW reservation with size %d: ", size);
    }
    int i = 0;
    for(i = 0; i < size; ++i) {
      int x;
      int y;
      id_to_pos(&x, &y, ((const char *)comm_buf)[i]);
      printf("%d, ", ((const char *)comm_buf)[i]);
    }
    printf("\n");

    // now we check if the new reservation is already part of our current reservation
    // we distinguish three cases
    // 1. the reservation is just 0
    // 2. the reservation is part of the current reservation
    // 3. the reservation is completely new




    // clean current commit value
    memset(&mc_value, 0, sizeof(merge_commit_value_t));

    if (size == 0) {
      own_arrival = 0; // reset own arrival since we have no intention for a reservation
    } else  {

      if (!part_of_current) {
        // we need to reset our own arrival time
        own_arrival = 0;
      }

      if (!WAIT_FOR_FREE_PATH || path_available(&mc_last_commited_value, &own_reservation, node_id)) {
        reserve_path(&mc_value, &own_reservation, node_id);
        set_own_arrival(&mc_value);
        printf("Try to reserve new path with arrival %d: \n", own_arrival);
      }
    }

    send_str("ack"); // ack the new reservation
  };

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

  process_start(&comm_process, NULL);
  process_start(&mc_process, NULL);
  PROCESS_WAIT_EVENT_UNTIL(ev == PROCESS_EVENT_POLL);
  PROCESS_END();
}

static void mc_round_begin(const uint16_t round_count, const uint8_t id){
  memcpy(&mc_commited_value, &mc_value, sizeof(merge_commit_value_t));

  printf("Starting round with arrival %d\n", mc_commited_value.arrivals[node_id-1]);
  mc_complete = merge_commit_round_begin(round_count, id, &mc_commited_value, &mc_phase, &mc_flags);
  mc_off_slot = merge_commit_get_off_slot();
  mc_round_count_local = round_count;
  process_poll(&mc_process);
}


int comp_node_with_arrival (const void * a, const void * b)
{
  if (*(uint32_t*) a < *(uint32_t*) b) {
    return -1;
  } else {
    // we don't care
    return 1;
  }
}

void merge_commit_merge_callback(merge_commit_t* rx_mc, merge_commit_t* tx_mc) {

  static int time_diff = 0;
  int start = RTIMER_NOW();
  int i = 0;
  int c = 0;
  merge_commit_value_t new;
  memset(&new, 0, sizeof(merge_commit_value_t));

  int size = 0;
  uint32_t node_id_with_arrivals[MAX_NODE_COUNT];

  for(i = 0; i < chaos_node_count; ++i) {
    if (ARRIVAL_TIMES) {
      // we can use max value here since either one of them is 0 or both have the same value...
      int arrival = MAX(rx_mc->value.arrivals[i], tx_mc->value.arrivals[i]);
      if (arrival > 0) {

        node_id_with_arrivals[size] = (arrival << 8) | (i&255);
        size++;
        // Do insertion sort with the array
        int j = size-1;
        while(j > 0 && node_id_with_arrivals[j-1] > node_id_with_arrivals[j]) {
          // we need to swap these elements
          uint32_t swap = node_id_with_arrivals[j];
          node_id_with_arrivals[j] = node_id_with_arrivals[j-1];
          node_id_with_arrivals[j-1] = swap;
          j--;
        }
      }
    } else {
      // No need to sort since we are adding the ids in the correct order...
      node_id_with_arrivals[size] =  (i&255);
      size++;
    }
  }


    //printf("Got reservations from ");
  for(i = 0; i < size; ++i) {

    // mask the id
    int id = (node_id_with_arrivals[i] & 255) + 1;

    //printf("%d (%d), ", id, node_id_with_arrivals[i] >> 8);
    merge_commit_value_t *plans[2];
    plans[0] = &tx_mc->value;
    plans[1] = &rx_mc->value;

    // TODO: We might want to try to add another route for ourselfs here
    // Add with a parameter

    c = 0;
    while(c < (sizeof(plans) / sizeof(merge_commit_t *))) {

      path_t path;
      extract_path(&path, plans[c], id);
      if (path.size > 0 && path_available(&new, &path, id)) {
        reserve_path(&new, &path, id);

        if (ARRIVAL_TIMES) {
          // we need to add the arrival time of this request!
          new.arrivals[id-1] = node_id_with_arrivals[i] >> 8;
        }

        break; // We could reserve the path ;)
      }
      c++;
    }
  }
    //printf("\n");

  // now copy the generated reservations
  memcpy(&tx_mc->value, &new, sizeof(merge_commit_value_t));

  int end = RTIMER_NOW();
  if (time_diff < end-start) {
    printf("New diff %d ms\n", 1000*time_diff/RTIMER_SECOND);
    time_diff = end-start;
  }
}


/* TODO Add test case

  static char comm_test[100];

  static char current_test = 0;// '?';
  static char test_buf[sizeof(comm_test)];

  // TODO: Add me as unit test!
  static int test_size = 0;

  while(1) {
    //memset(comm_test, current_test, sizeof(comm_test));

    int i = 0;
    for(i = 0; i < sizeof(comm_test); ++i) {
      comm_test[i] = rand() % 256;
    }
    test_size = rand() % (sizeof(comm_test)+1);


    send_data(comm_test, test_size);

    PROCESS_WAIT_EVENT_UNTIL(ev == serial_line_event_message && data != NULL);

        printf("received line: %d %s ", strlen((char *)data), (char *)data);
        current_test++;

    int size = decode_data(test_buf, (const char *) data);
printf(" decoded %d  %d \n", size, sizeof(comm_test)  );

if(!size == sizeof(comm_test) || memcmp(comm_test, test_buf, test_size)) {
printf(" ERROR %d vs %d \n", comm_test[0], test_buf[0]);
}
  }
 */