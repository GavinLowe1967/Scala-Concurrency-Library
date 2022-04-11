DIR = .

FILES = $(DIR)/ServerStack.class $(DIR)/TreeBarrier.class	\
$(DIR)/PrefixSumsShared.class $(DIR)/GridMaxTest.class		\
$(DIR)/SmoothSharedTest.class $(DIR)/BagOfTasksMonitor.class	\
$(DIR)/MonitorTerminatingPartialQueue.class

all:   $(FILES)

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

$(DIR)/ServerStack.class: $(DIR)/ImmutableStack.class

$(DIR)/GridMaxTest.class: $(DIR)/GridMax.class

$(DIR)/SmoothSharedTest.class: $(DIR)/SmoothShared.class

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
