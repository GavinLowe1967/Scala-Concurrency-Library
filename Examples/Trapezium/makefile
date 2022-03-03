DIR = .

all:   $(DIR)/TrapeziumTest.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/TrapeziumTest.class: $(DIR)/Trapezium.class



$(DIR)/%.class:     %.scala
	$(FSC)  $<
