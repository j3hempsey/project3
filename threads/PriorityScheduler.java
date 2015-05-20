package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	public static void selfTest()
	{
		/*
		 * Creates 3 threads with different priorities and runs them
		 */
		System.out.println("PriorityQueue test: START");
		KThread thread1 = new KThread(new Runnable(){
			public void run(){
				System.out.println("1st thread to run");
			}
		});
		KThread thread2 = new KThread(new Runnable(){
			public void run(){
				System.out.println("2nd thread to run");
			}
		});
		KThread thread3 = new KThread(new Runnable(){
			public void run(){
				System.out.println("3rd thread to run");
			}
		});
		Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1, 7);
		ThreadedKernel.scheduler.setPriority(thread2, 5);
		ThreadedKernel.scheduler.setPriority(thread3, 4);
		//ThreadedKernel.scheduler.setPriority(thread3, 7);
		Machine.interrupt().enable();
		
		thread3.fork();
		thread2.fork();
		thread1.fork();
		KThread.yield();
		System.out.println("PriorityQueue test: END");
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		//Sorted queue by priority
		private ArrayList<ThreadState> queue;

		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			queue = new ArrayList<ThreadState>();
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (queue.size() == 0 || queue.get(0) == null) return null;
			ThreadState next = queue.get(0);	//save next one to return 
			queue.remove(0);					//remove the next one
			Lib.debug(dbgSch, "[D] === Running thread: " + next.thread.toString() + " === [D]");
			return next.getThread();
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			if (queue.size() == 0 || queue.get(0) == null) return null;

			return queue.get(0);
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		public void addThread(ThreadState currentState){
			Lib.debug(dbgSch, "[D] === Adding thread to PriorityQueue(0): " 
				+ currentState.thread.toString() + " === [D]");
			queue.add(currentState);
			sort();
			return;
		}

		//Swaps two values in the tree 
		private void swap(int index1, int index2){
			ThreadState temp = queue.get(index1);
			queue.set(index1, queue.get(index2));
			queue.set(index2, temp);
		}

		public void sort(){
			Lib.debug(dbgSch, "[D] === Sorting Queue === [D]");
			if (queue.size() <= 1) return;		//the queue is only one long thus sorted
			for (int i = queue.size() - 1; i >= 0; --i){
				for (int j = 0; j < i; ++j){
					if (queue.get(j).getEffectivePriority() == queue.get(j + 1).getEffectivePriority()){
						if (queue.get(j).getTimeAdded() > queue.get(j + 1).getTimeAdded()){
							swap(j, j+1);
						}
					}
					else if (queue.get(j).getEffectivePriority() < queue.get(j + 1).getEffectivePriority()){
						swap(j, j+1);
					}
				}
			}

			Lib.debug(dbgSch, "[D] === PriorityQueue order after: === [D]");
			for (int i = 0; i < queue.size(); ++i)
			{
				Lib.debug(dbgSch, "[D] === \t" + i +") "+queue.get(i).thread.toString() 
				+ " Priority: " +queue.get(i).getPriority() + " === [D]");
			}
		}
		public int size(){
			return queue.size();
		}

		public ThreadState get(int i){
			if (i >= queue.size()) return null;
			return queue.get(i);
		}

		public void add(PriorityQueue q){
			for (int i = 0; i < q.size(); ++i){
				queue.add(q.get(i));
			}
		}
		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
			Lib.debug(dbgSch, "[D] === Setting thread time: " + thread.toString() + " === [D]");
			setTimeAdded(Machine.timer().getTime()); 		//set the threads time it was added
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			if (priority > effectivePriority) { 
				return priority;
			}
			else {
				return effectivePriority;	
			}
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;
			Lib.debug(dbgSch, "[D] === Setting thread priority: " + thread.toString() + " to: " + priority + " === [D]");
			this.priority = priority;
			if (PriorityDonateQ.size() > 0){
				if (PriorityDonateQ.transferPriority) {
					//calculateDonated();
				}
				PriorityDonateQ.sort();
			}

		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			waitQueue.addThread(this);
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			PriorityDonateQ.add(waitQueue); //add waitQueue for priority donation
			calculateDonated();
		}

		// Calculates the donated value of priorities
		private void calculateDonated(){
			//iterate through donation queue (if sorted, then it should be the first one in queue, but this is assuming it isn't)
			if (PriorityDonateQ.size() > 0) {
				if (PriorityDonateQ.transferPriority) { //if transferPriority is true
					for (int i = 0; i < PriorityDonateQ.size(); ++i){ 
						if (PriorityDonateQ.get(i).getEffectivePriority() > effectivePriority) {
							effectivePriority = PriorityDonateQ.get(i).getEffectivePriority();
						}
					}
				} else {
					effectivePriority = priority; 		//if no transfering effective is just the priority
				}
			}
			PriorityDonateQ.sort();
		}

		public void setTimeAdded(long time){
			this.timeAdded = time;
		}
		public long getTimeAdded(){
			return this.timeAdded;
		}

		//Returns the thread that has this state
		public KThread getThread(){
			return this.thread;
		}

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority = priorityDefault;
		
		/** Donated priority to the thread */
		protected int effectivePriority = 0;

		/** The relative position to all threads in queue */
		protected long timeAdded;
		
		/** Create a queue for donating priority **/
		protected PriorityQueue PriorityDonateQ = new PriorityQueue(true);
	}
	private static final char dbgSch = 's';

}
