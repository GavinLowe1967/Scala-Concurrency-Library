DIR = .

all:   $(DIR)/MinMaxTest.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/MinMaxTest.class: $(DIR)/MinMax.class

$(DIR)/%.class:     %.scala
	$(FSC)  $<
