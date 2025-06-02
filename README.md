## 💾 About

**MMS** is a dynamic memory manager with page-based memory management that works in concurrent/parallel (using the Producer-Consumer pattern) mode, developed as a project for the Operating Systems course.

## 🏗️ Project Structure

```bash
MMS/
├── memory/
│   ├── manager/
│   │   └── MemoryManager.java
│   ├── virtual/
│   │   ├── PageTableEntry.java
│   │   └── PageTable.java
│   └── physical/
│       ├── Frame.java
│       └── PhysicalMemory.java
├── process/
│   ├── Request.java
│   ├── RequestGenerator.java
│   └── RequestProducerConsumer.java
└── PerformanceChartGenerator.java
```

## 🔨 Tools

- [Java](https://docs.oracle.com/en/java/)
- [Maven](https://maven.apache.org)
- [JFreeChart](https://www.jfree.org/jfreechart/)

## 📄 Dependencies

- [`Java JDK`](https://www.oracle.com/java/technologies/downloads/) (mandatory)

## 🚀 How to Run

```bash
#Clone the project
$ git clone https://github.com/Ytalk/main-memory-simulator.git
```

```bash
#Enter directory
$ cd main-memory-simulator
```

```bash
#Run

#Linux/macOS:
$ chmod +x mvnw
$ ./mvnw compile
$ ./mvnw exec:java

#Windows:
$ ./mvnw compile
$ ./mvnw exec:java
```