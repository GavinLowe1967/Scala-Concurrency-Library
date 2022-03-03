DIR = .

all:   $(DIR)/Race.class 
# $(DIR)/Race2.class $(DIR)/SyncChanTest.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL



$(DIR)/%.class:     %.scala
	$(FSC)  $<
