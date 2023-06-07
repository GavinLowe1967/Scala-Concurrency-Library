DIR = ../ox/scl

CP = /home/gavin/Scala/SCL

FSC = fsc -deprecation  -cp $(CP) 
# -deprecation

all:   $(DIR)/package.class 

clean:
	rm $(DIR)/*.class $(DIR)/*/*.class $(DIR)/../cads/*/*.class; fsc -shutdown


##### Top-level

$(DIR)/ThreadGroup.class: $(DIR)/Stopped.class

##### Locks

LOCKDIR = $(DIR)/lock

LOCK_CPTS = $(LOCKDIR)/Barrier.class $(LOCKDIR)/CombiningBarrier.class	\
$(LOCKDIR)/Semaphore.class $(LOCKDIR)/CountingSemaphore.class		\
$(LOCKDIR)/Lock.class

##### Channels

CHANDIR = $(DIR)/channel

CHANNELS = $(CHANDIR)/SyncChan.class $(CHANDIR)/BuffChan.class $(CHANDIR)/OldSyncChan.class

# FIXME: Remove OldSyncChan

$(CHANDIR)/InPort.class $(CHANDIR)/OutPort.class: $(CHANDIR)/AltBranch.class
$(CHANDIR)/Chan.class: $(CHANDIR)/InPort.class $(CHANDIR)/OutPort.class $(DIR)/Stopped.class
$(CHANNELS): $(LOCKDIR)/Lock.class $(CHANDIR)/Chan.class
# Maybe following needs more
$(CHANDIR)/Alt.class: $(CHANDIR)/AltBranch.class $(DIR)/Stopped.class $(CHANDIR)/SyncChan.class # probably Chan.class

##### Linearizability testing

# Note: not all dependencies are captured below.

TESTDIR = ../ox/cads/testing

CADS_COLL_DIR = ../ox/cads/collection

# Components of testing framework
TESTING_CPTS = $(TESTDIR)/ThreadStates.class $(TESTDIR)/StateWrapper.class	\
$(TESTDIR)/Event.class $(TESTDIR)/Configuration.class				\
$(TESTDIR)/ConfigSet.class $(TESTDIR)/MyBitMapSet.class				\
$(TESTDIR)/UndoConfig.class $(TESTDIR)/QLTHistory.class

$(TESTDIR)/Configuration.class $(TESTDIR)/ConfigSet.class:	Testing/Configuration.scala Testing/ConfigSet.scala 
	$(FSC) -d .. Testing/Configuration.scala Testing/ConfigSet.scala

# Logging for sequential generic testers
TESTING_GEN_LOG = $(TESTDIR)/GenericThreadLog.class				\
$(TESTDIR)/GenericLog.class $(TESTDIR)/SharedLog.class	\
$(TESTDIR)/TSThreadLog.class $(TESTDIR)/TSLog.class

#Logging for sequential queue-oriented testers
TESTING_QUEUE_LOG = $(TESTDIR)/QueueThreadLog.class $(TESTDIR)/QueueLog.class   \
$(TESTDIR)/SharedQueueLog.class $(TESTDIR)/QueueTSThreadLog.class               \
$(TESTDIR)/QueueTSLog.class

$(TESTING_QUEUE_LOG): $(TESTDIR)/BoundedBuffer.class

# Logging for generic competition testers
TESTING_COMP_LOG = $(TESTDIR)/CompetitionThreadLog.class			\
$(TESTDIR)/CompetitionLog.class $(TESTDIR)/SharedCompetitionLog.class		\
$(TESTDIR)/TSCompetitionThreadLog.class $(TESTDIR)/TSCompetitionLog.class

# Logging for queue competition testers
TESTING_QUEUE_COMP_LOG = $(TESTDIR)/QueueCompetitionThreadLog.class     \
$(TESTDIR)/QueueCompetitionLog.class                                    \
$(TESTDIR)/SharedQueueCompetitionLog.class                              \
$(TESTDIR)/TSQueueCompetitionThreadLog.class                            \
$(TESTDIR)/TSQueueCompetitionLog.class

# All logging
TESTING_LOGS = $(TESTING_GEN_LOG) $(TESTING_QUEUE_LOG) $(TESTING_COMP_LOG)	\
$(TESTING_QUEUE_COMP_LOG)
$(TESTING_LOGS): $(TESTING_CPTS) $(TESTDIR)/TS.class

# Solvers for linearizability testing
TESTING_SOLVERS = $(TESTDIR)/Solver.class $(TESTDIR)/GenericSolver.class	\
$(TESTDIR)/WGGraph.class $(TESTDIR)/BFSJITLinTester.class			\
$(TESTDIR)/DFSGraphJITLinTester.class						\
$(TESTDIR)/WGLinearizabilityTester.class $(TESTDIR)/JITLinUndoTester.class	\
$(TESTDIR)/QueueLinSolver.class $(TESTDIR)/CompetitionSolver.class		\
$(TESTDIR)/LinearizabilityTester.class

$(TESTING_SOLVERS): $(TESTING_LOGS)

##### Debugging

DEBUGDIR = $(DIR)/debug

##### Complete package

$(DIR)/package.class: $(DIR)/ThreadGroup.class $(CHANNELS) $(DEBUGDIR)/Log.class $(LOCK_CPTS) $(TESTING_SOLVERS) $(CHANDIR)/Alt.class

##### Scaladoc

SCALADOCCP = /home/gavin/Scala/SCL:/home/gavin/Scala/CADS:/home/gavin/bin/scala-2.13.1/lib/scala-library.jar

SCALADOC_FILES = Stopped.scala Lock/*.scala Channel/*.scala Debug/Log.scala	\
Testing/*.scala ThreadGroup.scala package.scala

doc:
	scaladoc -classpath $(SCALADOCCP) -d ../Scaladoc $(SCALADOC_FILES)

##### Recipes

$(LOCKDIR)/%.class:     Lock/%.scala
	$(FSC) -d .. $<

$(CHANDIR)/%.class:     Channel/%.scala
	$(FSC) -d .. $<

$(TESTDIR)/%.class:	Testing/%.scala
	$(FSC) -d .. $<

$(CADS_COLL_DIR)/%.class: CADSCollection/%.scala
	$(FSC) -d .. $<

$(DEBUGDIR)/%.class:     Debug/%.scala
	$(FSC) -d .. $<

$(DIR)/%.class:     %.scala
	$(FSC) -d .. $<
