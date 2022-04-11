DIR = .

all:   $(DIR)/UnboundedBuff.class $(DIR)/AdaptiveTest.class $(DIR)/RingFold.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

$(DIR)/AdaptiveTest.class: $(DIR)/Adaptive.class $(DIR)/TrapeziumTest.class

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
