package nachos.threads;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
        this.tag_cvQuque_Map = new HashMap<Integer,LinkedList<Condition2>>(); 
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        boolean intStatus = Machine.interrupt().disable();
        //Machine.interrupt().restore(intStatus);
        Condition2 con1 = new Condition2(new Lock());
        if(tag_cvQuque_Map.get(new Integer(tag))==null)
            tag_cvQuque_Map.put(new Integer(tag), new LinkedList<Condition2>());
        Condition2 other = tag_cvQuque_Map.get(new Integer(tag)).pollLast();

        if(other==null){
            con1.value=value;
            tag_cvQuque_Map.get(new Integer(tag)).add(con1);
            con1.sleep();
            //player1 = KThread.currentThread();
        }

        else{
            int result = other.value;
            other.value = value;
            other.wake();
            Machine.interrupt().restore(intStatus);
            return result;




        }

        int result = con1.value;
        Machine.interrupt().restore(intStatus);
        return result;

    }
    private HashMap<Integer,LinkedList<Condition2>> tag_cvQuque_Map;


    
}
