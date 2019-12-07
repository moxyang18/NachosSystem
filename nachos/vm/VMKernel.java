package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		int n_ppgs = Machine.processor().getNumPhysPages();
		// initialize the PageFrame array and index it based on the ppn
		evict_list = new PageFrame[n_ppgs];
		lock3 = new Lock();
		for(int cur_ppn = 0; cur_ppn < n_ppgs; cur_ppn++) {
			evict_list[cur_ppn] = new PageFrame();
		}
		cond = new Condition(lock3);
		// should handle swap file here?
		swp_file = ThreadedKernel.fileSystem.open("Global_Swap", true);
		free_swp_pages = new LinkedList<Integer>();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		// close and remove the swapFile
		swp_file.close();
		ThreadedKernel.fileSystem.remove("Global_Swap");
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	
	public class PageFrame {
		
	/*	int vpn; 
		int ppn;
		public EvictTable () {
			vpn = -1;
			ppn = -1;
		}
		public EvictTable (int vpn, int ppn) {
			this.vpn = vpn;
			this.ppn = ppn;
		}

*/		VMProcess process;
		TranslationEntry pageEntry;
		boolean pinned;
		
		public PageFrame() {
			process = null;
			pageEntry = null;
			pinned = false;
		}

		public PageFrame(VMProcess cur_process, TranslationEntry cur_entry, boolean cur_pinned) {
			process = cur_process;
			pageEntry = cur_entry;
			pinned = cur_pinned;
		}


	}

	// need a pinCount to keep track of the total number of pages pinned
	public static int pinCount = 0;
	public static int victimTrack = 0;
	public static Lock lock3;
	public static Condition cond;
	// this static PageFrame is to keep track of each physical page and
	// its relevant process, whether it is pinned and the TranslationEntry
	protected static PageFrame[] evict_list;
	// using a single, global swap file across all processes.
	protected static OpenFile swp_file;
	// used to count the number of assigned swap number
	public static int swap_count = 0;
	// As with physical memory in project 2, a global free list works well. You can assume that the swap 
	// file can grow arbitrarily, and that there should not be any read/write errors. Assert if there are.
	public static LinkedList<Integer> free_swp_pages;


}
