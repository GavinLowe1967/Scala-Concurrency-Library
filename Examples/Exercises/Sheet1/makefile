DIR = .

all:   $(DIR)/PipeSort.class $(DIR)/Matrix.class $(DIR)/CountDups.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
