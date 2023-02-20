DIR = ../tests

BASIC_TESTS = $(DIR)/Race2.class $(DIR)/Deadlock.class	\
$(DIR)/ExceptionTest.class

CHANNEL_TESTS = $(DIR)/SyncChanTest.class $(DIR)/BuffChanTest.class	\
$(DIR)/TimeoutTest.class

ALT_TESTS = $(DIR)/AltTest.class $(DIR)/AltSharedTest.class	\
$(DIR)/AltDupTest.class $(DIR)/AltChannelTest.class $(DIR)/AltWithinTest.class

BARRIER_TESTS =  $(DIR)/BarrierTest.class	\
$(DIR)/CombiningBarrierTest.class $(DIR)/BarrierExperiment.class

TESTS = $(BASIC_TESTS) $(CHANNEL_TESTS) $(ALT_TESTS) $(BARRIER_TESTS)

all:   $(TESTS)	 

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL



$(DIR)/%.class:     %.scala
	$(FSC) -d .. $<
