TARGET ?= sky

CONTIKI_PROJECT = intersection-node
CONTIKI_WITH_CHAOS = 1
CHAOS_NODE_DYNAMIC=1

#ifeq ($(dynamic),1)
#	CHAOS_NODE_DYNAMIC=1
#else
#	ifeq ($(dynamic),2)
#		CHAOS_NODE_DYNAMIC=1
#	else
#		CHAOS_NODE_DYNAMIC=0
#	endif
#endif

CFLAGS += -DPROJECT_CONF_H=\"project-conf.h\"

#$(info initiator is [$(initiator)])

ifeq ($(initiator), 1)
    CFLAGS += -D CHAOS_IS_INITIATOR=1
else
    CFLAGS += -D CHAOS_IS_INITIATOR=0
endif

CONTIKI = ../../..
include $(CONTIKI)/Makefile.include

id: burn-nodeid.upload 

all: $(CONTIKI_PROJECT)

