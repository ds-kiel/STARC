#include "contiki.h"

#include <stdio.h> /* For printf() */
#include "net/netstack.h"
#include "dev/leds.h"
#include "dev/watchdog.h"
#include "node-id.h"

#define PERIOD (RTIMER_TO_VHT(RTIMER_SECOND)/1000UL)
/*---------------------------------------------------------------------------*/
PROCESS(hello_world_process, "Hello world process");
AUTOSTART_PROCESSES(&hello_world_process);
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(hello_world_process, ev, data)
{

PROCESS_BEGIN();
NETSTACK_MAC.off(0);

static uint16_t c = 0;
//  static uint32_t v, v0 = -1, t, t0;
//  static uint16_t l, r, h, d;
//  t0=0xf0000000UL+PERIOD;
//  while(1) {
//    leds_off(LEDS_GREEN);
//    do{
//      t = VHT_NOW();
//    } while(VHT_LT(t, t0));
//    leds_on(LEDS_GREEN);
//    watchdog_periodic();
//    t0 += PERIOD;
//
//    v = rtimer_arch_now_vht();
//    if( v < v0 ){
//  //      c++;
//  //      r = rtimer_arch_now();
//  //      d = rtimer_arch_now_dco();
//  //      printf("%u: v0 %lu v %lu l %u r %u h %u d %u\n",c, v0, v, l, r, h, d);
//      leds_blink();
//    }
//    v0 = v;
//  }

printf("Hello %u : %u\n", node_id, c++);

PROCESS_END();
}
/*---------------------------------------------------------------------------*/