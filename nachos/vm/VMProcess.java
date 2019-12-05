package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Override the same method in UserProcess, except it faults the page if not valid
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

		if (vaddr <0) return 0;
	
		byte[] memory = Machine.processor().getMemory();

		/* find the physical address using the pagetable and vaddr */
		
		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);

		if (cur_vpn >= numPages || cur_vpn <0) return 0;

		int bytes_read = 0;
//		for(int i = 0; i < numPages; i++) {
			// loop through the pagetable to find the target vpn
//			if (cur_vpn == pageTable[i].vpn && pageTable[i].valid) {
//				cur_ppn = pageTable[i].ppn;
//				break;
//			}
//		}

		// check if the page is invalid
		if(pageTable[cur_vpn].valid == false) {
			handlePgFault(vaddr);

			// if the page still remains invalid, return 0
			if(pageTable[cur_vpn].valid == false)
				return 0;
		}

		// get the corresponding physical page number
		int cur_ppn = pageTable[cur_vpn];

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
	 * Override the same method in UserProcess, except it faults the page if not valid
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

		// the virtual address must be valid		
		if(vaddr < 0) return 0;

		byte[] memory = Machine.processor().getMemory();

//		int numPhysPages = Machine.processor().getNumPhysPages();

		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);

		// the virtual page number should be valid
		if(cur_vpn >= numPages || cur_vpn <0) return 0;

		int bytes_written = 0;
//		for(int i = 0; i < numPages; i++) {
			// loop through the pagetable to find the target page, which should not be readonly
//			if (cur_vpn == pageTable[i].vpn && pageTable[i].valid && !pageTable[i].readOnly) {
//				cur_ppn = pageTable[i].ppn;
//				break;
//			}
//		}

		// if the page is not valid, call handlePgFault to prepare new page
		if(pageTable[cur_vpn].valid == false) {
			handlePgFault(vaddr);
			cur_ppn = pageTable[cur_vpn].ppn;
		}

		// if the current ppn is still not modified/ invalid, simply return 0
//		if(cur_ppn <0 || !pageTable[cur_vpn].valid) return 0;

		int cur_ppn = pageTable[cur_vpn].ppn;

		// update the used bit
		pageTable[cur_vpn].used = true;

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
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		int numPhysPages = Machine.processor().getNumPhysPages();

		if (numPages > numPhysPages) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// create a pageTable of the needed number of page entries
		pageTable = new TranslationEntry[numPages];

	 	// initilaize all entries as invalid
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		
		return true;
		
		
		//return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
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
			// handle the case when pagefault exception happens
			case Processor.exceptionPageFault:
				handlePgFault(processor.readRegister(Processor.regBadVAddr));
				break;
			default:
				super.handleException(cause);
				break;
		}
	}

	/* This is the helper method that handles the pagefault when it happens
	 * Prepare the demanded page when needed.	
	 */
	private void handlePgFault(int demandAddr) {
		int demandVpn = Processor.pageFromAddress(demandAddr);

		//////////////////////////////////////// can the faulting vpn be greater than numPages? 
		if(demandVpn >= numPages) { 
			return; 
		}

		UserKernel.lock1.acquire();

		int section_vpn = -1;
		int cur_vpn = -1;
//		int num_assigned = -1;         /////////////////////////////////////// remove?
//		boolean page_found = false;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				cur_vpn = section.getFirstVPN() + i;
				section_vpn = cur_vpn;
				// assign the page frame number of the physical page
				//System.out.println("the size of physical pages is:" + UserKernel.free_physical_pages.size());
				
				// if a match of vpn is found, we know what to load in
				if (demandVpn == cur_vpn) {
					
					// if there is no physical page left to be assigned
					if(UserKernel.free_physical_pages.isEmpty()) {
						//////////////////////////////// cannot assign if no free physical page
					}

					// if we can assign a physical page
					else {
						int ppn = UserKernel.free_physical_pages.removeLast();

						// add the page to the loaded list
						loaded_pages.add(ppn);

						// Load a page from this segment of the current pagetable into physical memory.
						section.loadPage(i, ppn);

//						num_assigned++;                ////////////////////////// remove?

						// if this coff section is read-only create the entry with
						// setting the readOnly bit to be true
						pageTable[cur_vpn] = new TranslationEntry(cur_vpn, ppn, true, section.isReadOnly(), false, false);
//						page_found = true;
//						break;

						// return after prepared the demand page
						UserKernel.lock1.release();
						return;
					}
				}
				
				// otherwise, look for the match in code/data section of COFF
				else continue;
					
			}

		}

		// find the match page if not in COFF's code/data section
		for (int k = cur_vpn+1; k<numPages; k++) {
			section_vpn++;

			if(section_vpn == demandVpn && !UserKernel.free_physical_pages.isEmpty()) {

				//System.out.println("the size of physical pages is after :" + UserKernel.free_physical_pages.size());
				int ppn = UserKernel.free_physical_pages.removeLast();
				// add the page to the loaded list
				loaded_pages.add(ppn);
				
				// create a zero_filled byte array
				byte[] zero_filler = new byte[pageSize];
				for (byte c: zero_filler)
					c = 0;

				// zero fill the physical page ???
				System.arraycopy(zero_filler, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);
		
				UserKernel.lock1.release();
				return;
			}

		}

	}


	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
