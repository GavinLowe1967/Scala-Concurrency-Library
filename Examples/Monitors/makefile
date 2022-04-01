DIR = .

all:   $(DIR)/Race2.class $(DIR)/Race3.class $(DIR)/BusyWait.class $(DIR)/ProducerConsumer.class $(DIR)/ReadersWritersTest.class $(DIR)/SyncChan.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/ReadersWritersTest.class: $(DIR)/ReadersWritersLock.class

$(DIR)/%.class:     %.scala
	$(FSC)  $<
