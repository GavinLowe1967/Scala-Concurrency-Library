DIR = .

all:   $(DIR)/PhilsLeft.class $(DIR)/PhilsButler.class $(DIR)/PhilsTO.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
