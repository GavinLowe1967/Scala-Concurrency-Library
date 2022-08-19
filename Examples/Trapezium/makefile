DIR = .

all:   $(DIR)/TrapeziumTest.class $(DIR)/TrapeziumExperiment.class

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL:/home/gavin/Scala/Util -deprecation

FSCX = fsc -cp .:/home/gavin/Scala/SCL:/home/gavin/Scala/Util

$(DIR)/Trapezium.class $(DIR)/TrapeziumBag.class: $(DIR)/TrapeziumT.class

$(DIR)/TrapeziumTest.class $(DIR)/TrapeziumRun.class: $(DIR)/Trapezium.class $(DIR)/TrapeziumBag.class $(DIR)/TrapeziumBagObjects.class

$(DIR)/TrapeziumExperiment.class: $(DIR)/TrapeziumRun.class

# $(DIR)/TrapeziumExperiment.class: TrapeziumExperiment.scala
# 	$(FSCX) $<
# TrapeziumExperiment.scala

$(DIR)/%.class:     %.scala
	$(FSC)  $<
