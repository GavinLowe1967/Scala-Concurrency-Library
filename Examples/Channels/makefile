DIR = .

all:   $(DIR)/Mults4.class $(DIR)/QSortChannels.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL



$(DIR)/%.class:     %.scala
	$(FSC)  $<
