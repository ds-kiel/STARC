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

#$(info initiator is [$(initiators)])

CFLAGS += -D CHAOS_INITIATORS=$(initiators)

CONTIKI = ../../..
include $(CONTIKI)/Makefile.include

id: burn-nodeid.upload 

all: $(CONTIKI_PROJECT)

