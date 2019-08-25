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
 *         Valentin Poirot <poirotv@chalmers.se>
 *         Patrick Rathje <mail@patrickrathje.de>
 */

#ifndef _JOIN_H_
#define _JOIN_H_

#include "chaos-control.h"

typedef uint16_t node_id_t;
typedef uint8_t node_index_t;

extern const chaos_app_t join;

void join_init( void );
int join_last_round_is_complete( void );
int join_get_joined_nodes_not_committed_num( void );
int join_is_in_round( void );
uint16_t join_get_commit_slot( void );
void join_print_nodes( void );
int join_get_index_for_node_id(node_id_t node_id);
uint16_t join_get_off_slot( void );

#if TESTBED == indriya
#define FAULTY_NODE_ID (140)
#define FAULTY_NODE_COUNT 97
#else //disable check
#define FAULTY_NODE_ID (0)
#define FAULTY_NODE_COUNT (255)
#endif

#ifndef NODE_LIST_LEN
#define NODE_LIST_LEN 10  //describes how many nodes can join in a single round
#endif

#if FAULTY_NODE_ID || FAULTY_NODE_COUNT
typedef struct {
  uint16_t debug_pos;
  uint16_t slot;
  uint8_t info;
  uint8_t slot_type;
  uint8_t slot_status;
} join_debug_t;
typedef enum {
  RX_ERR=1, TX_ERR_BEFORE=2, TX_ERR_AFTER=4, TX_ERR=(TX_ERR_BEFORE | TX_ERR_AFTER),
  DUMMY_ERR_BEFORE=8, DUMMY_ERR_AFTER=16, DUMMY_ERR=(DUMMY_ERR_BEFORE | DUMMY_ERR_AFTER),
} join_error_codes_t;

#define SET_ERR_CODE(X, ERR_CODE) ((X)=((X)|(ERR_CODE)))

extern volatile uint8_t rx_pkt_crc_err[129];
extern volatile uint8_t rx_pkt_copy[129];
extern volatile join_debug_t join_debug_var;
#endif /* FAULTY_NODE_ID */


#ifndef JOIN_LOG_FLAGS
#define JOIN_LOG_FLAGS 0
#endif

#if JOIN_LOG_FLAGS
extern uint8_t chaos_join_commit_log[JOIN_ROUND_MAX_SLOTS];
extern uint8_t chaos_join_flags_log[JOIN_ROUND_MAX_SLOTS];
#endif

typedef struct __attribute__((packed)) {
    node_id_t node_id;
    node_index_t chaos_index;
} join_node_map_entry_t;

typedef struct __attribute__((packed)) {
    uint16_t config;
    uint8_t node_count;
    union {
        uint8_t commit_field;
        struct{
            uint8_t                   //control flags
                slot_count :5,       //number of slots into which nodes wrote their ids
                overflow :1,              /* available join slots too short */
                commit :2;                /* commit join */
        };
    };

    node_id_t slots[NODE_LIST_LEN]; //slots to write node ids into /* assigned indices */
    node_index_t indices[NODE_LIST_LEN]; //slots to write node ids into /* assigned indices */
} join_data_t;


extern node_id_t joined_nodes[MAX_NODE_COUNT];
extern uint16_t join_config;

inline int join_merge_data(join_data_t *tx, join_data_t *rx, uint8_t *delta_flag);
inline int add_node(node_id_t id, uint8_t chaos_node_count_before_commit);
int join_binary_search( join_node_map_entry_t array[], int size, node_id_t search_id );
void join_reset_nodes_map();
void join_init_free_slots();

uint16_t join_get_config();
void join_set_config(uint16_t config);
void join_increase_config();

void join_do_sort_joined_nodes_map();
#endif /* _JOIN_H_ */