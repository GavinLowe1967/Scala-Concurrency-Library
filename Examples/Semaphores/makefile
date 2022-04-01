DIR = .

all:   $(DIR)/BarrierTest.class $(DIR)/PartialQueueTest.class $(DIR)/MutexQueue.class $(DIR)/ReadersWritersTest.class $(DIR)/SharedSyncChanTest.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/BarrierTest.class:  $(DIR)/SemaphoreBarrier.class

$(DIR)/PartialQueueTest.class: $(DIR)/SemaphorePartialQueue.class

$(DIR)/ReadersWritersTest.class: $(DIR)/ReadersWritersLockSemaphore.class

$(DIR)/SharedSyncChanTest.class: $(DIR)/SyncChanSemaphores.class

$(DIR)/%.class:     %.scala
	$(FSC)  $<
