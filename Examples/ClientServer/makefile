DIR = .

all:   $(DIR)/RATest.class 

$(DIR)/RATest.class: $(DIR)/RA.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/%.class:     %.scala
	$(FSC)  $<
