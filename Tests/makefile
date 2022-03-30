DIR = ../tests

BASIC_TESTS = $(DIR)/Race2.class $(DIR)/Deadlock.class	\
$(DIR)/ExceptionTest.class

CHANNEL_TESTS = $(DIR)/SyncChanTest.class $(DIR)/BuffChanTest.class	\
$(DIR)/TimeoutTest.class

ALT_TESTS = $(DIR)/AltTest.class $(DIR)/AltSharedTest.class	\
$(DIR)/AltDupTest.class

TESTS = $(BASIC_TESTS) $(CHANNEL_TESTS) $(ALT_TESTS) $(DIR)/BarrierTest.class

all:   $(TESTS)	 

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL



$(DIR)/%.class:     %.scala
	$(FSC) -d .. $<
