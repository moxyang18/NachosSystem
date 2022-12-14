package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashMap;
import java.util.Hashtable;

//import javax.annotation.processing.Processor;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	// this is the file table of the file descriptors and the corresponding file
	private OpenFile[] fileTable = new OpenFile[16];
	public Lock lock;
	public Condition cv;
	public int pid;
	protected Hashtable<Integer,UserProcess> children = new Hashtable<Integer, UserProcess>();
	public Integer exitStatus;
	protected UserProcess parent;
	private boolean exitAbn;

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		UserKernel.lock2.acquire();

		loaded_pages = new LinkedList<Integer>();
		for (int i=0; i<16; i++){
			fileTable[i] = null;
		}
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
		this.pid = UserKernel.processIDcounter++;
		++UserKernel.processNum;
		lock = new Lock();
		cv = new Condition(lock);
		exitStatus = null;
		UserKernel.lock2.release();
		exitAbn = false;
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		/* find the physical address using the pagetable and vaddr */
		
		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);
		int cur_ppn = -1;
		int bytes_read = 0;
		for(int i = 0; i < numPages; i++) {
			// loop through the pagetable to find the target vpn
			if (cur_vpn == pageTable[i].vpn && pageTable[i].valid) {
				cur_ppn = pageTable[i].ppn;
				break;
			}
		}

		// if the current ppn is not modified/ invalid, simply return 0
		if(cur_ppn <0) return 0;

		// calculate the ppn's address 
		int cur_ppn_addr = cur_ppn*pageSize + cur_vpn_offset;

		// if the address is out of bounds, simply return 0
		if(cur_ppn_addr>=memory.length) return 0;
		int amount = Math.min(length, pageSize-cur_vpn_offset);


		System.arraycopy(memory, cur_ppn_addr, data, offset, amount);
		// update the length left to be read
		length -= amount;
		// update the bytes having been read
		bytes_read += amount;
		// update the data's offset/ next pos to be read
		offset += amount;

		Boolean notExist = true;
		// while the virtual pages are contiguous, the physical pages are not
		// so we need to loop through to find the next physical page to fetch
		// data if there are still more bytes required to read
		while (length>0) {
			// update the virtual page number
			cur_vpn++;
			for (int i = 0; i < numPages; i++) {
				if(cur_vpn == pageTable[i].vpn && pageTable[i].valid) {
					notExist = false;
					cur_ppn = pageTable[i].ppn;
					break;
				}
			}

			// calc the next physical page's address
			cur_ppn_addr = cur_ppn*pageSize; // + cur_vpn_offset;
			// return the number of bytes already read if any of the following conditions
			// is satisfied
			if (cur_ppn<0 || cur_ppn_addr >= memory.length || notExist) return bytes_read;

			amount = Math.min(length, pageSize);
			System.arraycopy(memory, cur_ppn_addr, data, offset, length);

			// update all flags/ counters
			notExist = true;
			bytes_read += amount;
			length -= amount;
			offset += amount;
		}

		return bytes_read;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

//		int numPhysPages = Machine.processor().getNumPhysPages();

		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);
		int cur_ppn = -1;
		int bytes_written = 0;
		for(int i = 0; i < numPages; i++) {
			// loop through the pagetable to find the target page, which should not be readonly
			if (cur_vpn == pageTable[i].vpn && pageTable[i].valid && !pageTable[i].readOnly) {
				cur_ppn = pageTable[i].ppn;
				break;
			}
		}

		// if the current ppn is not modified/ invalid, simply return 0
		if(cur_ppn <0) return 0;

		// calculate the ppn's address 
		int cur_ppn_addr = cur_ppn*pageSize + cur_vpn_offset;

		// if the address is out of bounds, simply return 0
		if(cur_ppn_addr>=memory.length) return 0;
		int amount = Math.min(length, pageSize-cur_vpn_offset);


		System.arraycopy(data, offset, memory, cur_ppn_addr, amount);
		// update the length left to be read
		length -= amount;
		// update the bytes having been read
		bytes_written += amount;
		// update the data's offset/ next pos to be read
		offset += amount;

		Boolean notExist = true;
		// while the virtual pages are contiguous, the physical pages are not
		// so we need to loop through to find the next physical page to fetch
		// data if there are still more bytes required to read
		while (length>0) {
			// update the virtual page number
			cur_vpn++;
			for (int i = 0; i < numPages; i++) {
				if(cur_vpn == pageTable[i].vpn && pageTable[i].valid && !pageTable[i].readOnly) {
					notExist = false;
					cur_ppn = pageTable[i].ppn;
					break;
				}
			}

			// calc the next physical page's address
			cur_ppn_addr = cur_ppn*pageSize; // + cur_vpn_offset;
			// return the number of bytes already read if any of the following conditions
			// is satisfied
			if (cur_ppn<0 || cur_ppn_addr >= memory.length || notExist) return bytes_written;

			amount = Math.min(length, pageSize);
			System.arraycopy(data, offset, memory, cur_ppn_addr, length);

			// update all flags/ counters
			notExist = true;
			bytes_written += amount;
			length -= amount;
			offset += amount;
		}

		return bytes_written;

	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {

			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}


		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		UserKernel.lock1.acquire();

		int numPhysPages = Machine.processor().getNumPhysPages();

		if (numPages > numPhysPages) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// create a pageTable of the needed number of page entries
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		int section_vpn = -1;
		int num_assigned = -1;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				section_vpn = vpn;
				// assign the page frame number of the physical page
				//System.out.println("the size of physical pages is:" + UserKernel.free_physical_pages.size());
				int ppn = UserKernel.free_physical_pages.removeLast();


				// add the page to the loaded list
				loaded_pages.add(ppn);

				// Load a page from this segment of the current pagetable into physical memory.
				section.loadPage(i, ppn);

				num_assigned++;

				// if this coff section is read-only create the entry with
				// setting the readOnly bit to be true
				pageTable[num_assigned] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
					
			}
		}

		// create entries for the rest of the pages
		for (int k = num_assigned+1; k<numPages; k++) {
			section_vpn++;
			//System.out.println("the size of physical pages is after :" + UserKernel.free_physical_pages.size());
			int ppn = UserKernel.free_physical_pages.removeLast();
			// add the page to the loaded list
			loaded_pages.add(ppn);
			pageTable[k] = new TranslationEntry(section_vpn, ppn, true, false, false, false);
		}

		UserKernel.lock1.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.lock1.acquire();
		for(int i = 0; i<loaded_pages.size(); i++) {
			UserKernel.free_physical_pages.add(loaded_pages.removeLast());
		}
		UserKernel.lock1.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(pid !=0) return -1;
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExec(int file, int argc, int argv) {
		
		
		String filename = readVirtualMemoryString(file,256);	
		// the filename cannnot be null and the argc cannot be smaller
		// than 0, filename also has to end with ".coff", with has length
		// > 5
		if(filename == null || argc < 0 || filename.length() <=5 ||
			! filename.substring(filename.length()-5, filename.length()).equals(".coff")) {
			return -1;
		}

		//Create string array to represent the "args"
		String[] args = new String[argc];
		
		//The buffer to read virtual memory into
		byte[] buffer = new byte[4];

		//allocating program arguments to args[]
		for (int i = 0; i < argc; i++) 
		{
			buffer = new byte[4];
			// advance the address by i*sizeOf(int) every time
			int read = readVirtualMemory(argv+i*4, buffer);
			if(read != buffer.length)
				return -1;
            
			args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0),256);
			
			//error occur, return -1
			if (args[i] == null)	return -1;
				
		}
		
		// create a new process for execute
		UserProcess child = newUserProcess();
		
	//	children.put(child.pid, child);
		
		//child.parent = this;
		boolean succ = child.execute(filename, args);
		
		// if such a process can be made
		if(succ) {
			children.put(child.pid, child);
			child.parent = this;
			
			return child.pid;
		}

		// otherwise, we need to decrease the number of processes 
		// since in the constructor of the new process we incremented that
		--UserKernel.processNum;

		return -1;
	}

	
	/**
	 * Handle the exit() system call.
	 */
	
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.


		exitStatus = status;

		//clean up
		for(int i = 0; i < 16; i++){
		/*	if(fileTable[i] != null){
				fileTable[i].close();
				fileTable[i] = null;
			}*/

			// call handleClose to close the Openfile
			this.handleClose(i);
		  }
		unloadSections();
		coff.close();
		Set<Integer> keys = children.keySet();

        for(Integer c_pid: keys){
		
			UserProcess c =children.get(c_pid);
			c.lock.acquire();
			c.parent = null;
			c.lock.release();;
        }

		if(parent != null){
			parent.lock.acquire();
			parent.children.remove(this.pid);
			parent.lock.release();
		}

		UserKernel.lock2.acquire();
		UserKernel.processNum--;
		if(UserKernel.processNum == 0){
			UserKernel.lock2.release();
			Kernel.kernel.terminate();

		}
		else{
			UserKernel.lock2.release();
			KThread.finish();
		}

		
		//return status;
		return 0;
	}

	private int handleJoin(int processID, int status){

		if(!children.containsKey(processID)){

		  return -1;
		}

		UserProcess child = children.get(processID);

		child.thread.join();
	/*
		child.lock.acquire();
			
		Integer childStatus = child.exitStatus;
		if (childStatus == null)
		{
			lock.acquire();
			child.lock.release();
			cv.sleep();
			lock.release();
			
			child.lock.acquire();
			childStatus = child.exitStatus;

		}
		child.lock.release();

		*/

		Integer childStatus = child.exitStatus;

		children.remove(processID);// can't be joined later	
		
		
		if (!child.exitAbn){

			byte[] statusAry = Lib.bytesFromInt(childStatus.intValue());
			writeVirtualMemory(status, statusAry);

		
			return 1;
		}	
		else
			return 0;
	  }




	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);


		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:

			//if no syscall to handleexception but there is an exception
			exitAbn = true;
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}


	public int handleCreate(int fn){
		String filename = readVirtualMemoryString(fn, 256);
		if(filename == null ) return -1;

		OpenFile openFile = ThreadedKernel.fileSystem.open(filename, true); //truncate????
		if (openFile == null) return -1;
		for (int i=2; i<16 ;i++){ //never refer stream
			if (fileTable[i]==null){
				fileTable[i]= openFile;
				return i;
			}
		}
		return -1; // table is full
	}


	public int handleOpen(int fn){
		String filename = readVirtualMemoryString(fn, 256);
		if(filename == null ) return -1;

		//System.out.println("the filename is " + filename);

		OpenFile openFile = ThreadedKernel.fileSystem.open(filename, false); //truncate????
		if (openFile == null) return -1;
		for (int i=2; i<16 ;i++){                     //never refer stream
			if (fileTable[i]==null){
				fileTable[i]= openFile;
				return i;
			}
		}

		return -1; // table is full
	}

	public int handleClose(int fd){
		//Lib.assertTrue(fd >= 0 || fd <= 15);
		//Lib.assertTrue(fileTable[fd] != null);
		if (fd>15 || fd <0 || fileTable[fd]==null)
			return -1;
		
		else{
			fileTable[fd].close();
			fileTable[fd] = null;
			return 0;

		}
	}

	public int handleUnlink(int fn){
		String filename = readVirtualMemoryString(fn, 256);
		if(filename == null ) return -1;
		else{

			if(ThreadedKernel.fileSystem.remove(filename))
				return 0;
			else return -1;

		}
	}

	/* This function reads from the buffer and writes to the file in fd table*/
	public int handleWrite(int fd, int addr, int length){
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (addr < 0 || addr >= memory.length||addr +length >= memory.length)
			return -1;

		if(fd<0 || fd >15 || fileTable[fd] == null ){
			//System.out.println("bp 0");
	
			return -1;
		}

		if(length<0) return -1;

		if(length <= pageSize){

			byte[] buffer = new byte[length];
			//readVirtualMemory(addr,buffer,0, length);
			int valid_read = readVirtualMemory(addr,buffer,0, length);
		
			//System.out.println("write was called with fd =" + fd);

			//return fileTable[fd].write(0,buffer,0,length);
			return fileTable[fd].write(buffer,0,length);
			
		}
		else{

			int count = 0;
			int pos = 0;
			int n = 0;
			byte[] buffer = new byte[pageSize];
			while (pos < length){
				// calculate amount left 
				// min( _ , _ )i
				//
				// initialzie buffer of size minValue
			
				if(pos +pageSize <= length){
					//System.out.println(pageSize);
					// read from virtual memory specified by addr to buffer 
					// with offset pos, reading in total pageSize bytes
	
					//readVirtualMemory(addr,buffer,0,pageSize);
					int valid_read = readVirtualMemory(addr,buffer,0, pageSize);
					
					//for (int i = 0; i < 1024; i++) {}
						//System.out.println((char)buffer[i]);
					
					// write from the buffer to the file specified by fd
					n = fileTable[fd].write(buffer,0,pageSize);
	
					// advance addr to be read in VM by pageSize
					addr += pageSize;
	
					if (n == -1) return n; //indicate fault or disk is full
					pos += pageSize;
					count +=n;
				}
				else{
					//System.out.println("bp 5");
					//readVirtualMemory(addr,buffer,0,length-pos);
					int valid_read = readVirtualMemory(addr,buffer,0, length-pos);
	
		

					n = fileTable[fd].write(buffer,0,length-pos);
					if(n == -1) return n; //indicate fault or disk is full
					pos = length;
					count += n;
				}
			
			}
			if (count != length) return -1; // not writing correct number of bytes
			else return count;
		}

	}

	/* This function reads from a file into the buffer specified by addr.
	 * */
	public int handleRead(int fd, int addr, int length){
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (addr < 0 || addr >= memory.length)
			return -1;

		if(fd<0 || fd >15 || fileTable[fd] == null ){
			return -1;
		}

		if(length<0) return -1;

		if(length <= pageSize){
			byte[] buffer = new byte[length];
			int n = fileTable[fd].read(buffer, 0, length);
//			int n = fileTable[fd].read(0,buffer,0,length);
			if (n == -1) return -1;
			writeVirtualMemory(addr,buffer,0, n);
			return n;

		}
		else{
//	int vm_addr = addr;
			int count = 0;
			int pos = 0;
			int n = 0;
			byte[] buffer = new byte[pageSize];
			while (pos < length || n != 0){
				if(pos +pageSize <= length){

//					System.out.println(addr);
					n = fileTable[fd].read(buffer, 0, pageSize);
//					System.out.println(n);
				//	n = fileTable[fd].read(pos,buffer,0,pageSize);
					if (n == -1) return n; //indicate fault or disk is full
					writeVirtualMemory(addr,buffer, 0, n);  ////////pos->0
					addr += pageSize;
					pos += pageSize;
					count +=n;
				}
				else{
					n = fileTable[fd].read(buffer, 0, length-pos);
				//	n = fileTable[fd].write(pos,buffer,0,length-pos);
					if(n == -1) return n; //indicate fault or disk is full
					writeVirtualMemory(addr,buffer, 0, length-pos); //// pos->0
					addr += length-pos;
					pos = length;
					count += n;
				}
			}
//			System.out.println(count);
			if (count > length) return -1; // not reading correct number of bytes, actual > requested
			else return count;
		}

	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	/* a local var to track all of the loaded pages */
	protected LinkedList<Integer> loaded_pages;

}

