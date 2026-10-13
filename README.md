# FacultyProject-CEBP
CEBP (ro. PCBE) project - Year 4, Sem 1

This is a faculty project for the Event-Based & Concurrent Programming (en. CEBP, ro. PCBE) course at Politehnica University of Timișoara.
This project is designed and intended to be completed by four students, those being the owner of this repo (SovietskySoldatie) and all codeowners (BiaG14, Luca-Adrian Mihut (lukettoOoO) & Tatiana Lazar (per1cle)).

Project original specs may be found under the "original specs" folder.
The full project specifications may be found at https://docs.google.com/document/d/1L7y-9PixbeBWSjfRTmJsURariE3YkuhAN-OvhlYhYVU/edit?tab=t.0
Requirements may be found under the "reqs" folder.

## TO-DOs:
- [] Add specs & reqs
	- [x] Add project original specifications
	- [] Decompose project specifications
	- [] Add requirements
	- [] Decompose project requirements

- [] Define project architecture
	- [] Define software architecture
		- [x] Create package structure
		- [x] Create interfaces
		- [] Improve (?) architecture
			- [] Add documentation
				- [] Add .docx file
				- [] Add diagrams
			- [] Better define logic separation
			- [] Better define interfaces
			- [] Future-proof classes, interfaces, other suggestions, etc.
			- [] Keep architecture up-to-date

- [] Define software design
	- TBD
- [] Implement code
	- [x] Initial ramp-up
		- [x] Implement base app logic
		- [x] Implement dependencies related logic
		- [x] Implement scheduler & resource management logic
		- [x] Implement requests logic
	- [] Polish and review code
		- [] Verify Scheduler component for thread-safety
	- [] Create an executable demo
		- [] Demo preparations
			- [] Simulation extensions
				- [] In size
				- [] Try to get overlaps
				- [] Mini-infinite run
			- [] Fault introductions
				- [] OfficeQueue :: blocking queue vs non-blocking :: data incoherence // race condition
				- [] Counter :: BusyWaiting :: while runs continuously // Luca knows more
				- [] OfficialStamp :: mutex vs non-mutex :: get 2 stamps for same document
				- [] Config :: not checking for graph loops // eventually link to graph non-readiness
				- [] Config + Scheduler :: Singleton not instantiated/multiple instantiation :: race condition
				- [] Scheduler :: race conditions
				- [] Client/Scheduler + Config :: ask for 2+ documents simultaneously :: deadlock/livelock
				- [] Config + (maybe) Office :: mutable vs immutable requiredDocuments Collection :: race condition :: also deadlock (if needed document is erased before value is found) // need to check
		- [] Polish the demo
	- [] Present the demo
- [] Implement tests
	- [] Move tests to src/test/java folder
	- [] Add test documentation
		- TBD
