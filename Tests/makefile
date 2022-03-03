DIR = ../tests

BASIC_TESTSS = $(DIR)/Race2.class $(DIR)/Deadlock.class

TESTS = $(BASIC_TESTS) $(DIR)/SyncChanTest.class $(DIR)/BuffChanTest.class

all:   $(TESTS)	 

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL



$(DIR)/%.class:     %.scala
	$(FSC) -d .. $<
