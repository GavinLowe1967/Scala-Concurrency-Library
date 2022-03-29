DIR = .

all:   $(DIR)/QueueTest.class $(DIR)/PartialQueueTest.class $(DIR)/WordPath.class

$(DIR)/QueueTest.class: $(DIR)/TotalQueue.class

$(DIR)/PartialQueueTest.class: $(DIR)/PartialQueue.class

$(DIR)/GraphSearch.class: $(DIR)/TerminatingPartialQueue.class

$(DIR)/WordPath.class: $(DIR)/GraphSearch.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
