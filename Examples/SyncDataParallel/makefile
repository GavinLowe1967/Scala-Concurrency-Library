DIR = .

all:   $(DIR)/BarrierTest.class $(DIR)/PrefixSums.class $(DIR)/JacobiTest.class $(DIR)/JacobiExperiment.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

$(DIR)/BarrierTest.class: $(DIR)/ServerBarrier.class

$(DIR)/JacobiTest.class: $(DIR)/Jacobi.class

$(DIR)/JacobiExperiment.class: JacobiExperiment.scala $(DIR)/Jacobi.class
	fsc -cp .:/home/gavin/Scala/SCL:/home/gavin/Scala/Util JacobiExperiment.scala

$(DIR)/%.class:     %.scala
	$(FSC)  $<
