DIR = .

all:   $(DIR)/MonitorSlot.class $(DIR)/ProducerConsumer2.class $(DIR)/SharedSyncChanTest.class $(DIR)/BoundedPartialQueueTest.class $(DIR)/BarberC.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/SharedSyncChanTest.class: $(DIR)/SharedSyncChan.class

$(DIR)/BoundedPartialQueueTest.class: $(DIR)/BoundedMonitorPartialQueue.class

$(DIR)/%.class:     %.scala
	$(FSC)  $<
