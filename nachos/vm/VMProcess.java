package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.vm.VMKernel.PageFrame;

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
		VMKernel.lock3.acquire();
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if (vaddr <0) {
			VMKernel.lock3.release();
			return 0;
		}
	

		int num_phyPages = Machine.processor().getNumPhysPages();
		byte[] memory = Machine.processor().getMemory();

		/* find the physical address using the pagetable and vaddr */
		
		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);
		if (cur_vpn <0 || cur_vpn >= numPages) {
			VMKernel.lock3.release();	
			return 0;
		}

		int bytes_read = 0;
		// check if the page is invalid
		if(pageTable[cur_vpn].valid == false) {
			handlePgFault(vaddr+bytes_read);
			// if the page still remains invalid, return 0
			if(pageTable[cur_vpn].valid == false) {
				VMKernel.lock3.release();	
				return 0;
			}
			// otherwise, this physical page is pinned, update the associated vars
			// done outside the loop
		}

		// get the corresponding physical page number
		int cur_ppn = pageTable[cur_vpn].ppn;

		// calculate the ppn's address 
		int cur_ppn_addr = cur_ppn*pageSize + cur_vpn_offset;
		// if the address is out of bounds, simply return 0
		if(cur_ppn_addr <0 || cur_ppn_addr>=memory.length) {
			VMKernel.lock3.release();	
			VMKernel.cond.wake();	
			return 0;
		}

		// set the pinned bit of the pageFrame to restrict access to the entry
		// and increment the number of pinned counts
		VMKernel.evict_list[cur_ppn].pinned = true;
		VMKernel.pinCount += 1;

		int amount = Math.min(length, pageSize-cur_vpn_offset);
		System.arraycopy(memory, cur_ppn_addr, data, offset, amount);

		// after the data successfully transferred to the array, we unpin the physical
		// page, update total number of pinned pages, and wake if valid
		VMKernel.evict_list[cur_ppn].pinned = false;
		VMKernel.pinCount -= 1;
		if(VMKernel.pinCount < num_phyPages) {
			VMKernel.cond.wake();
		}
		// update the length left to be read
		length -= amount;
		// update the bytes having been read
		bytes_read += amount;
		// update the data's offset/ next pos to be read
		offset += amount;
		// this page is used now
		pageTable[cur_vpn].used = true;

		// while the virtual pages are contiguous, the physical pages are not
		// so we need to loop through to find the next physical page to fetch
		// data if there are still more bytes required to read
		
		while (length>0) {

			// update the virtual page number
			cur_vpn++;
			if (cur_vpn >= numPages) {
				VMKernel.lock3.release();	
				return 0;
			}
			// check if the page is invalid
			if(pageTable[cur_vpn].valid == false) {
				handlePgFault(vaddr+bytes_read);
				// if the page still remains invalid, return 0
				if(pageTable[cur_vpn].valid == false) {
					VMKernel.lock3.release();	
					return bytes_read;
				}
			}
			// get the corresponding physical page number
			cur_ppn = pageTable[cur_vpn].ppn;
			// calc the next physical page's address
			cur_ppn_addr = cur_ppn*pageSize; // + cur_vpn_offset;
			// return the number of bytes already read if any of the following conditions
			// is satisfied
			if (cur_ppn<0 || cur_ppn_addr >= memory.length) {
				VMKernel.lock3.release();		
				return bytes_read;
			}
			amount = Math.min(length, pageSize);
			VMKernel.evict_list[cur_ppn].pinned = true;
			VMKernel.pinCount += 1;
			System.arraycopy(memory, cur_ppn_addr, data, offset, length);
			VMKernel.evict_list[cur_ppn].pinned = false;
			VMKernel.pinCount -= 1;
			if(VMKernel.pinCount < num_phyPages) {
				VMKernel.cond.wake();
			}
			// update all flags/ counters
//			notExist = true;
			bytes_read += amount;
			length -= amount;
			offset += amount;
			pageTable[cur_vpn].used = true;
		}
		VMKernel.lock3.release();	
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
		VMKernel.lock3.acquire();
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		// the virtual address must be valid		
		if(vaddr < 0) {
			VMKernel.lock3.release();	
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
	
		int num_phyPages = Machine.processor().getNumPhysPages();

		/* Extract the page number component and the offset from a 32-bit address.*/
		int cur_vpn = Processor.pageFromAddress(vaddr);
		int cur_vpn_offset = Processor.offsetFromAddress(vaddr);

		// the virtual page number should be valid
		if(cur_vpn >= numPages || cur_vpn <0) {
			VMKernel.lock3.release();		
			return 0;
		}
		int bytes_written = 0;
		// if the page is not valid, call handlePgFault to prepare new page
		if(pageTable[cur_vpn].valid == false) {
			handlePgFault(vaddr+bytes_written);
			if(pageTable[cur_vpn].valid == false) {
				VMKernel.lock3.release();	
				return 0;
			}
		}

		if(pageTable[cur_vpn].readOnly) {
			VMKernel.lock3.release();	
			return 0;
		}

		int cur_ppn = pageTable[cur_vpn].ppn;

		// update the used bit
		pageTable[cur_vpn].used = true;

		// calculate the ppn's address 
		int cur_ppn_addr = cur_ppn*pageSize + cur_vpn_offset;

		// if the address is out of bounds, simply return 0
		if(cur_ppn_addr>=memory.length) {
			VMKernel.lock3.release();	
			return 0;
		}
		int amount = Math.min(length, pageSize-cur_vpn_offset);
		// set the pinned bit of the pageFrame to restrict access to the entry
		// and increment the number of pinned counts
		VMKernel.evict_list[cur_ppn].pinned = true;
		VMKernel.pinCount += 1;
		// If the page is modified, set dirty bit to true.
		// by checking the amount to write is greater than 0
		if(amount >0)	pageTable[cur_vpn].dirty = true;
		System.arraycopy(data, offset, memory, cur_ppn_addr, amount);
		// after the data successfully transferred to the array, we unpin the physical
		// page, update total number of pinned pages, and wake if valid
		VMKernel.evict_list[cur_ppn].pinned = false;
		VMKernel.pinCount -= 1;
		if(VMKernel.pinCount < num_phyPages) {
			VMKernel.cond.wake();
		}	
		// update the length left to be read
		length -= amount;
		// update the bytes having been read
		bytes_written += amount;
		// update the data's offset/ next pos to be read
		offset += amount;

//		boolean notExist = true;
		// while the virtual pages are contiguous, the physical pages are not
		// so we need to loop through to find the next physical page to fetch
		// data if there are still more bytes required to read
		int count = 1;
		while (length>0) {
			
			// update the virtual page number
			cur_vpn++;

			if(cur_vpn >= numPages || cur_vpn <0 || pageTable[cur_vpn].readOnly) {
				VMKernel.lock3.release();	
				return bytes_written;
			}
			// if the page is not valid, call handlePgFault to prepare new page
			if(pageTable[cur_vpn].valid == false) {
				handlePgFault(vaddr+bytes_written);
				count++;
				if(pageTable[cur_vpn].valid == false) {
					VMKernel.lock3.release();	
					return bytes_written;
				}
			}

			cur_ppn = pageTable[cur_vpn].ppn;

			// calc the next physical page's address
			cur_ppn_addr = cur_ppn*pageSize; // + cur_vpn_offset;
			// return the number of bytes already read if any of the following conditions
			// is satisfied
			if (cur_ppn<0 || cur_ppn_addr >= memory.length) {
				VMKernel.lock3.release();	
				return bytes_written;
			}
			amount = Math.min(length, pageSize);
			// pin the physical page to restrict evict untimely
			VMKernel.evict_list[cur_ppn].pinned = true;
			VMKernel.pinCount += 1;
			if(amount >0)	pageTable[cur_vpn].dirty = true;
			System.arraycopy(data, offset, memory, cur_ppn_addr, length);
			// unpin the p p now allow eviction
			VMKernel.evict_list[cur_ppn].pinned = false;
			VMKernel.pinCount -= 1;
			if(VMKernel.pinCount < num_phyPages) {
				VMKernel.cond.wake();
			}	
			// update all flags/ counters
//			notExist = true;
			bytes_written += amount;
			length -= amount;
			offset += amount;
			pageTable[cur_vpn].used = true;
		}
		VMKernel.lock3.release();	
		return bytes_written;

	}



	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		UserKernel.lock1.acquire();
		int numPhysPages = Machine.processor().getNumPhysPages();

		// create a pageTable of the needed number of page entries
		// set every entry to invalid
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}
		UserKernel.lock1.release();
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

	/* This is the helper method that will be called in handlePgFault
	 * to evict a physical page when no free physical pages available
	 */
	private void evict() {
		byte[] memory = Machine.processor().getMemory();
		int ind_evict = -1;
		int num_phyPages = Machine.processor().getNumPhysPages();
		// in the while loop until finding a valid page to evict
		while(true) {		
		//		for(int victimInd = 0; victimInd<Machine.processor().getNumPhysPages(); victimInd++) {

			//when there are no free pages available and all the pages
			//are pinned meaning, we have to swap out a page but we do
			//not have any eligible pages. So the request for a physical
			//page has to be delayed until either a physical page becomes
			//free or a page is unpinned.		
			if(VMKernel.pinCount >= num_phyPages) {
				VMKernel.cond.sleep();        //code will continue once cv1 is waken
			}
			// if the entry is used, go to the next victim index to look for
			// the next unused entry to evict
			if(VMKernel.evict_list[VMKernel.victimTrack].pageEntry.used ||
				VMKernel.evict_list[VMKernel.victimTrack].pinned) {
				// in order to use the clock algorithm, increment victimTrack
				// in the range of all possible indices, accomplished by mod
				VMKernel.victimTrack = (VMKernel.victimTrack+1)%num_phyPages;
				continue;
			}
			// otherwise the current victim page is not used, evict it
			// break out of the while loop with the found victim index
			break;
		}

		// store the victim index and increment the victimTrack variable to
		// meet the requirement of the clock algorithm
		ind_evict = VMKernel.victimTrack;
		PageFrame evict_frame = VMKernel.evict_list[ind_evict];
		VMKernel.victimTrack = (VMKernel.victimTrack+1)%num_phyPages;

		//If the page is dirty, though, the kernel must save the page 
		//contents in the swap file on disk.
		// how to achieve this ??
		// SWAP OUT a page if the page to be evicted is dirty by Openfile.write()
		if(evict_frame.pageEntry.dirty){

			int swap_ind = -1;
			// discussion says dont swap if section is readonly
			// cant be dirty if is readonly
			// if the ids aren't enough
			if(VMKernel.free_swp_pages.isEmpty()){
				swap_ind = VMKernel.swap_count++;
			}
			
			// if not empty, remove
			else{
				swap_ind = VMKernel.free_swp_pages.removeLast();
			}
			// swap out by writing
			VMKernel.evict_list[ind_evict].pageEntry.vpn = swap_ind;
			VMKernel.swp_file.write(swap_ind*pageSize, memory,
					Processor.makeAddress(VMKernel.evict_list[ind_evict].pageEntry.ppn, 0), pageSize);
		}

		// otherwise the page can be used directly
		// add the ppn that can be used back to the free physical page
		int evict_ppn = evict_frame.pageEntry.ppn;
		UserKernel.free_physical_pages.add(evict_ppn);
		// remove the page from the process at the same time
		evict_frame.process.loaded_pages.remove(evict_ppn);
		// Invalidate PTE and TLB entry of the victim page
		evict_frame.pageEntry.valid = false;

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
		int num_phyPages = Machine.processor().getNumPhysPages();
		byte[] memory = Machine.processor().getMemory();
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
						this.evict();
					}
					// we can thus always assign a physical page
					int ppn = UserKernel.free_physical_pages.removeLast();
					// add the page to the loaded list
					loaded_pages.add(ppn);

					// SWAP IN a page if the page to be accessed is dirty, indicating
					// it has been swapped out before
					if(pageTable[demandVpn].dirty) {

						// ??????????????????????????????????????????????

						VMKernel.swp_file.read(pageSize * pageTable[cur_vpn].vpn, memory,
							 Processor.makeAddress(ppn, 0), pageSize);

						VMKernel.free_swp_pages.add(pageTable[cur_vpn].vpn);	 
						pageTable[cur_vpn] = new TranslationEntry(cur_vpn, ppn, true, false, true, true);

						//pageTable[cur_vpn] = new TranslationEntry(cur_vpn, ppn, true, section.isReadOnly(), true, true);
					}

					// otherwise the accessed page is clean, same as before
					else {
						// Load a page from this segment of the current pagetable into physical memory.
						section.loadPage(i, ppn);
						// if this coff section is read-only create the entry with
						// setting the readOnly bit to be true
						// also set the used bit to true 
						pageTable[cur_vpn] = new TranslationEntry(cur_vpn, ppn, true, section.isReadOnly(), true, false);
					}
					// set for the evict_list, for each ppn set the same entry as for the pageTable
					VMKernel.evict_list[ppn].pageEntry = pageTable[cur_vpn];
					VMKernel.evict_list[ppn].process = this;
					// return after prepared the demand page
					UserKernel.lock1.release();
					return;	
				}		
				// otherwise, look for the match in code/data section of COFF
				else continue;
			}
		}

		// find the match page if not in COFF's code/data section
		for (int k = cur_vpn+1; k<numPages; k++) {
			section_vpn++;
			if(section_vpn == demandVpn){
				if(UserKernel.free_physical_pages.isEmpty()) {
					this.evict();
				}
				// after gaining the free physical page, or there have been enough pp
				if(!UserKernel.free_physical_pages.isEmpty()) {
				
					int ppn = UserKernel.free_physical_pages.removeLast();
					// add the page to the loaded list
					loaded_pages.add(ppn);
					// SWAP IN a page if the page to be accessed is dirty, indicating
					// it has been swapped out before
					if(pageTable[demandVpn].dirty) {

//						VMKernel.swp_file.read();
//						VMKernel.free_swp_pages.add()

						VMKernel.swp_file.read(pageSize * pageTable[section_vpn].vpn, memory,
							 Processor.makeAddress(ppn, 0), pageSize);

						VMKernel.free_swp_pages.add(pageTable[section_vpn].vpn);	 
						pageTable[section_vpn] = new TranslationEntry(section_vpn, ppn, true, false, true, true);
					}

					else {
						//System.out.println("the size of physical pages is after :" + UserKernel.free_physical_pages.size());
						// create a zero_filled byte array
						byte[] zero_filler = new byte[pageSize];
						for (byte c: zero_filler)
							c = 0;
						// zero fill the physical page ???
						System.arraycopy(zero_filler, 0, memory, Processor.makeAddress(ppn, 0), pageSize);	
						// also set the TLBeNTRY
						pageTable[section_vpn] = new TranslationEntry(section_vpn, ppn, true, false, true, false);
					}
					VMKernel.evict_list[ppn].pageEntry = pageTable[section_vpn];
					VMKernel.evict_list[ppn].process = this;
					UserKernel.lock1.release();
					return;
				}
			}
		}
	}


	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

}
