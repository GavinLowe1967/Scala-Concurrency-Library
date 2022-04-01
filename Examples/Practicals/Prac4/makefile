all: SleepingTutorTest.class

clean:
	rm *.class; fsc -shutdown

FSC = fsc -cp .:/home/gavin/Scala/SCL

SleepingTutorTest.class: SleepingTutor.class

%.class:	%.scala
	$(FSC) $<
