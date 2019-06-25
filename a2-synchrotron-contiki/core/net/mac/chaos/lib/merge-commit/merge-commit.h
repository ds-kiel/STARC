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

#ifndef _MERGE_COMMIT_H_
#define _MERGE_COMMIT_H_

#include "chaos.h"
#include "testbed.h"
#include "chaos-config.h"

#include "join.h"

#define MERGE_COMMIT_LOG_FLAGS 0


#ifndef MERGE_COMMIT_SLOT_LEN_MSEC
#define MERGE_COMMIT_SLOT_LEN_MSEC 1
#endif

#define MERGE_COMMIT_SLOT_LEN  (MERGE_COMMIT_SLOT_LEN_MSEC*(RTIMER_SECOND/1000))    //1 rtimer tick == 2*31.52 us

//force radio off after MERGE_COMMIT_ROUND_MAX_SLOTS slots
#ifndef MERGE_COMMIT_ROUND_MAX_SLOTS
#define MERGE_COMMIT_ROUND_MAX_SLOTS   (350)
#endif

#define MERGE_COMMIT_SLOT_LEN_DCO      (MERGE_COMMIT_SLOT_LEN*CLOCK_PHI)    //TODO needs calibration


#define PHASE_MERGE 4
#define PHASE_COMMIT 8

#ifndef MERGE_COMMIT_VALUE_STRUCT_CONTENT
#define MERGE_COMMIT_VALUE_STRUCT_CONTENT uint32_t x;
#endif

// struct definitions


typedef struct __attribute__((packed)) {
    MERGE_COMMIT_VALUE_STRUCT_CONTENT
} merge_commit_value_t;


typedef struct __attribute__((packed)) {
    char gap_0[1];
    join_data_t join_data;
    merge_commit_value_t value;
    uint8_t phase;
    uint8_t flags_and_leaves[];
} merge_commit_t;



#define MERGE_COMMIT_WANTED_JOIN_STATE_LEAVE 0
#define MERGE_COMMIT_WANTED_JOIN_STATE_JOIN 1

extern uint8_t merge_commit_wanted_join_state;

int merge_commit_round_begin(const uint16_t round_number, const uint8_t app_id, merge_commit_value_t* merge_commit_value, uint8_t* phase, uint8_t** final_flags);

int merge_commit_is_pending(const uint16_t round_count);

int merge_commit_get_flags_length(void);

uint16_t merge_commit_get_off_slot();

int merge_commit_has_joined();
int merge_commit_has_left();

int merge_commit_agreed();

int merge_commit_did_tx();

#endif /* _MERGE_COMMIT_H_ */
