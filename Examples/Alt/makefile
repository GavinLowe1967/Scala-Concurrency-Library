DIR = .

all:   $(DIR)/Phils.class $(DIR)/PhilsLog.class $(DIR)/Buff2.class Tagger.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
