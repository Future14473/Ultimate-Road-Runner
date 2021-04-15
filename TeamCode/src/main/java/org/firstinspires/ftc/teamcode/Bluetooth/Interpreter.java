package org.firstinspires.ftc.teamcode.Bluetooth;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;


public class Interpreter {
    public boolean DEBUG = false;
    private final String setPlainText = "\033[0;0m";
    private final String setBoldText = "\033[0;1m";
    private boolean profanity;

    // state of interpreter
    boolean immediate = true;

    // address of first pointer in the linked list that comprises the dictionary
    int HERE = -1;

    // address of initial opcode, or main() for c programmers.
    // will be populated later
    int ENTRY_POINT = -1;

    // input
    public scan input;
    // output
    public interface outputListener{
        void outputInvoked(String message);
    }
    private outputListener outputListener;
    public void setOutputListener(outputListener o){
        outputListener = o;
    }
    // memory. Double array
    private List<Integer> memory = new ArrayList<>();
    // native java objects, extension of memory (get with negative addresses on stack)
    private List<Object> objects = new ArrayList<>();
    // starting point for native java methods and fields
    private Object nativeRoot = null;
    public void setNativeRoot(Object o){nativeRoot = o;}
    // data stack
    private Stack stack = new Stack();
    // call stack for nested execution
    private Stack call_stack = new Stack();

    // link up names of primitives to their java code
    private HashMap<Integer, String> primitive_words = new HashMap<>();

    void output(Object s){
        outputListener.outputInvoked(s.toString());
    }
    void outputln(Object s){
        output(s.toString() + "\n");
    }

    /**
     * Interpreter use only, do not confuse with Forth word
     * Appends a new a word definition stub to memory array (no instructions)
     */
    void create(String name){
        // add link in linked list to prev word
        memory.add(HERE);
        // update head of linked list
        HERE = memory.size();
        // add name
        write_string(name, memory.size());
        // add immediate flag (default non immediate)
        memory.add(0);
    }

    /**
     * Interpreter use only. Sets the most recently defined word as immediate
     */
    void set_immediate(){
        memory.set(HERE + memory.get(HERE), 1);
    }

    /**
     * Interpreter use only. Searches through dictionary
     * for a word that matches the name in the string
     * @return Address of word, if found. -1 if not found
     */
    int search_word(String name)
    {
        int here = HERE;
        while(here != -1)
        {
            String word_name = read_string(here);
            if(name.equals(word_name))
                return here;
            //move 'here' back
            here = memory.get(here-1);
        }
        return -1;
    }

    /**
     * Convert word address to address of its immediate flag
     */
    public int addressToFlag(int address)
    {
        return address + memory.get(address);
    }
    /**
     * Convert word address to address of its first instruction
     */
    public int addressToOp(int address)
    {
        return address + memory.get(address) + 1;
    }


    /**
     * See other definition of declare primitive. Defaults
     * to non-immediate
     */
    void declarePrimitive(String name)
    {
        declarePrimitive(name, false);
    }

    /**
     * Word only for interpreter use. Creates primitive word definition stubs in memory
     * @param name name of word
     * @param immediate Flags for immediate word
     */
    void declarePrimitive(String name, boolean immediate)
    {   // add link in linked list
        create(name);

        // register word as primitive
        // allow the relevant java code to be found
        primitive_words.put(HERE, name);

        if(immediate)
            memory.set(addressToFlag(HERE), 1);

    }

    {
        //allow primitive words to be found in dictionary
        declarePrimitive("new");
        declarePrimitive("/");
        declarePrimitive("fields");
        declarePrimitive("setField");
        declarePrimitive("methods");
        declarePrimitive("seeobj");
        declarePrimitive("native");
        declarePrimitive("seestack");
        declarePrimitive("seemem");
        declarePrimitive("seerawmem");
        declarePrimitive("memposition");
        declarePrimitive("here");
        declarePrimitive("print");
        declarePrimitive("return", true);
        declarePrimitive("word");
        declarePrimitive("stack>mem");
        declarePrimitive("[", true);
        declarePrimitive("]");
        declarePrimitive("literal", true);
        declarePrimitive("read");
        declarePrimitive("donothing");
        declarePrimitive("set");
        declarePrimitive("+");
        declarePrimitive("-");
        declarePrimitive("*");
        declarePrimitive("=");
        declarePrimitive("dup");
        declarePrimitive("swap");
        declarePrimitive("drop");
        declarePrimitive("over");
        declarePrimitive("not");
        declarePrimitive("and");
        declarePrimitive("or");
        declarePrimitive("xor");
        declarePrimitive("branch");
        declarePrimitive("branch?");
        declarePrimitive("stringliteral");
        declarePrimitive("read-string");
        declarePrimitive("create");
        declarePrimitive("profanity");
        declarePrimitive("quit");

        create(":");
        memory.add(search_word("create"));
        memory.add(search_word("stringliteral"));
        memory.add(search_word("literal"));
        memory.add(0);
        memory.add(search_word("stack>mem"));
        memory.add(search_word("]"));
        memory.add(search_word("return"));

        create(";");
        memory.add(search_word("["));
        memory.add(search_word("literal"));
        memory.add(search_word("return"));
        memory.add(search_word("stack>mem"));
        memory.add(search_word("return"));
        set_immediate();

        ENTRY_POINT = memory.size();
        memory.add(search_word("donothing"));
        memory.add(search_word("return"));
    }

    public volatile List<String> nexttoks = new ArrayList<>();
    public void begin() {
        // run file start.f
        Scanner in = new Scanner(startup);
        input = new scan(){
            @Override
            public boolean hasNext() {
                return in.hasNext();
            }

            @Override
            public String next() {
                return in.next();
            }
        };
        outputln("Startup file found");
        repl();

        // run from terminal input
        input = new scan() {

            @Override
            public boolean hasNext() {
                while (nexttoks.size() == 0 && !forceEnd){

                }
                return !forceEnd;
            }

            @Override
            public String next() {
                if(hasNext()){
                    System.out.println("Taking " + nexttoks.get(0));
                    return nexttoks.remove(0);
                }
                return null;
            }
        };
        outputln("Commence Interactive Remote REPL");
        repl();
        output("REPL is over");
    }

    public Interpreter(outputListener outputL, Object root){
        setOutputListener(outputL);
        setNativeRoot(root);
    }
    
    private void repl() {
        //DEBUG = true;

        /* All instructions must be stored in memory, even the
         * uncompiled immediate mode stuff. ENTRY_POINT is a pointer
         * to an integer of reserved space for instructions
         * executed directly from the input stream. At ENTRY_POINT + 1
         * is a return which will clear the pointer to ENTRY_POINT
         * after the instruction there executes
         */

        // set call stack to execute the reserved instruction address
        call_stack.add(ENTRY_POINT+1);

        // set the reserved address to nothing; the program will populate this
        // accordingly as it parses the input stream
        memory.set(ENTRY_POINT, search_word("donothing"));

        // set the instruction after to return
        memory.set(ENTRY_POINT+1, search_word("return"));

        while (true){
            int word_address = memory.get(call_stack.last());

            /* Instructions within immediate word during compile time should be executed,
             * but the design of the REPL loop compiles the instructions anyway
             * Check for when the call stack has 2 or more frames, then enforce immediate mode
             */

            // immediate words and immediate mode will cause the current instruction to be executed
            if(immediate || memory.get(addressToFlag(word_address)) == 1 || (call_stack.size()>=2))
            {   // primitive or forth word?
                if(primitive_words.containsKey(word_address))
                {
                    if(DEBUG)
                        outputln(" r::" + read_string(word_address));

                    // execute primitive
                        try {
                    primitive(primitive_words.get(word_address));
                        } catch (IllegalAccessException e) {
                        } catch (InvocationTargetException e) {
                        } catch (ClassNotFoundException e) {
                        } catch (NoSuchMethodException e) {
                        } catch (InstantiationException e){
                        }catch (Exception e) {
                            outputln("Uncaught Exception " + e.toString());}

                }else{
                    if(DEBUG)
                        outputln(" rf::" + read_string(word_address));

                    // execute forth word
                    call_stack.add(word_address + memory.get(word_address));
                }
            }else{
                if(DEBUG)
                    outputln(" c::" + read_string(word_address));

                // compile word
                memory.add(word_address);
            }

            // check for empty call stack, if so get the next instruction
            if (call_stack.size() == 0)
            {
                String next_word = nextInstruction();

                // end of input stream
                if(next_word == null) return;

                if(DEBUG)
                    outputln("\nNext word: " + next_word);

                // do not allow the call stack to be incremented since we just reset the call stack
                continue;
            }

            //advance code pointer
            call_stack.incrementLast();
        }
    }

    /**
     * executes the relevant primitive based on its name
     */
    
    void primitive(String word) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InstantiationException {
        switch (word) {
            case "fields": output(ReflectionMachine.fields(getObject())); break;
            case "methods" : output(ReflectionMachine.methods(getObject())); break;
            case "setField" : setField(); break;
            case "new" : /*newOperator();*/ break;
            case "native" : addObject(nativeRoot); break;
            case "/" : dotOperator(); break;
            case "donothing" : output(""); break;
            case "print" : outputln(stack.pop()); break;
            case "return" : call_stack.remove(call_stack.size() - 1); break;
            case "word" : stack.add(search_word(input.next())); break;
            case "stack>mem" : memory.add(stack.pop()); break;
            case "here" : stack.add(HERE); break;
            case "[" : immediate = true; break;
            case "]" : immediate = false; break;
            case "seestack" : {
                for (int tok : stack)
                    output(tok + " ");
                outputln("<-"); break;
            }
            case "seeobj" : {
                for (Object tok : objects)
                    output(tok.getClass().getName() + " ");
                outputln("<-"); break;
            }
            case "seemem" : show_mem(); break;
            case "seerawmem" : outputln(memory); break;
            case "stringliteral" : write_string(input.next(), memory.size()); break;
            case "read-string" : outputln(read_string(stack.pop())); break;
            case "literal" : {
                call_stack.incrementLast();
                stack.add(memory.get(call_stack.last())); break;
            }
            case "memposition" : stack.add(memory.size()); break;
            case "create" : {
                memory.add(HERE);
                HERE = memory.size(); break;
            }
            case "read" : stack.add(memory.get(stack.pop())); break;
            case "set" : { //value, address <-- top of stack
                int address = stack.pop();
                if (address == memory.size()) memory.add(stack.pop());
                else memory.set(address, stack.pop()); break;
            }
            case "+" : stack.add(stack.pop() + stack.pop()); break;
            case "=" : stack.add(stack.pop() == stack.pop() ? 1 : 0); break;
            case "-" : stack.add(-stack.pop() + stack.pop()); break;
            case "*" : stack.add(stack.pop() * stack.pop()); break;
            case "not" : stack.add(stack.pop() == 0 ? 1 : 0); break;
            case "and" : stack.add(stack.pop() & stack.pop()); break;
            case "or" : stack.add(stack.pop() | stack.pop()); break;
            case "xor" : stack.add(stack.pop() ^ stack.pop()); break;
            case "swap" : {
                int p = stack.pop();
                stack.add(stack.size() - 1, p); break;
            }
            case "over" : {
                stack.add(stack.get(stack.size()-2));
                break;
            }
            case "dup" : stack.add(stack.last()); break;
            case "drop" : stack.remove(stack.size() - 1); break;
            case "branch" : //advance pointer by instruction at current pointer position
                call_stack.set(call_stack.size() - 1, call_stack.last() + memory.get(call_stack.last() + 1));
                break;
            case "branch?" : {
                if (stack.last() == 0) //advance pointer by instruction at current pointer position
                    call_stack.setLast(call_stack.last() + memory.get(call_stack.last() + 1));
                else //jump over the offset by 1
                    call_stack.incrementLast();
                stack.pop();
                break;
            }

            //case "quit" : System.exit(0);
        }
    }
    void addObject(Object o){
        objects.add(o);
        stack.add(-objects.size());
    }
    Object getObject(){
        return objects.get( (-stack.pop())-1 );
    }


    void newOperator() throws IllegalAccessException, InvocationTargetException, InstantiationException {
        String classname = input.next();
        Constructor[] constructors;
            try {
        constructors = Class.forName(classname).getConstructors();
            } catch (ClassNotFoundException e){
                outputln(classname + " was not found. Fully qualified names required");
        return;
            }

        outputln("Constructor params: ");
        Class[] o = constructors[0].getParameterTypes();
        for(Class i:o){
            output(i + " ");
        }

        Object[] params = new Object[constructors[0].getParameterTypes().length];
        for(int i=0; i<params.length; i++){
            params[i] = stack.pop();
        }
        addObject(constructors[0].newInstance(params));
    }

    // (value_to_set caller_object '' name of attribute -- )
    void setField() throws InvocationTargetException, IllegalAccessException {
        // the calling object
        Object actor = getObject();
        // the name of the object's field or method
        String fieldOrClass = input.next();

        // get actual type of attribute
        Object attribute = ReflectionMachine.getByString(actor, fieldOrClass);

        if (attribute == null) {
            outputln("field or class " + fieldOrClass + " not found as attribute");
        }

        else if(attribute instanceof Method){
            outputln("Selected method, not Field");
        }

        // get the value of field
        else if(attribute instanceof Field) {
            Field thefield = ((Field)attribute);
            if (thefield.getType() == int.class ||
                    thefield.getType() == Integer.class ||
                    thefield.getType() == Double.class ||
                    thefield.getType() == Float.class) {
                thefield.setInt(actor, stack.pop());
            }
            else {//is object
                outputln("Field is not settable to Int");
            }
        }
    }

    // (caller object '' name of attribute -- return value or address of returned object)
    void dotOperator() throws InvocationTargetException, IllegalAccessException {
        // the calling object
        Object actor = getObject();
        // the name of the object's field or method
        String fieldOrClass = input.next();

        // get actual type of attribute
        Object attribute = ReflectionMachine.getByString(actor, fieldOrClass);

        if (attribute == null) {
            outputln("field or class " + fieldOrClass + " not found as attribute");
        }

        // call a method and push return to stack
        else if(attribute instanceof Method){
            Method themethod = ((Method)attribute);

            // get parameters
            Object[] params = new Object[themethod.getParameterTypes().length];

            for(int i=0; i<params.length;i++){
                int stackElem = stack.pop();
                // stack has object address or integer?
                //params[i] = (stackElem < 0)? objects.get(-stackElem-1):stackElem;
                params[i] = stackElem;

            }
            // invoke
            Object returnval = themethod.invoke(actor, params);

            // manage return as object or integer
            if(returnval instanceof Integer){
                stack.add((int)returnval);
            } else {// is object
                objects.add(returnval);
                stack.add(-objects.size());
            }

        }
        // get the value of field
        else if(attribute instanceof Field) {
            Field thefield = ((Field)attribute);
            if (thefield.getType() == int.class || thefield.getType() == Integer.class) {
                stack.add(thefield.getInt(actor));
            }
            else {//is object
                addObject(thefield.get(actor));
            }
        }
    }

    /**
     * Take next instruction from input stream and prepare it
     * for execution by placing the relevant opcode in memory
     * and reinitializing the call stack
     */
    String nextInstruction()
    {
        // usually EOF when reading from file
        if(!input.hasNext()) {
            outputln("...end of file");
            return null;
        }

        // get next token from input
        String next_Word = input.next();
        // if it's a number, then deal with the number and skip to next
            try{
        int val = Integer.valueOf(next_Word);
        if(immediate) {
            stack.add(val);
        }else{
            memory.add(search_word("literal"));
            memory.add(val);
        }
        return nextInstruction();
            }catch(NumberFormatException e){}

        // reset call stack to execute from ENTRY_POINT
        call_stack.add(ENTRY_POINT);

        // find address of word identified by token
        int address = search_word(next_Word);

        // does address correspond to existing word?
        if(search_word(next_Word) == -1)
        {   // word not found
            outputln("word " + next_Word + " not found");

            //set empty instruction at ENTRY_POINT
            memory.set(ENTRY_POINT, search_word("donothing"));

        }else{
            // word found, set new instruction at ENTRY_POINT
            memory.set(ENTRY_POINT, address);
        }
        return next_Word;
    }

    /**
     * See the other definition of write_string
     * This defaults to writing to memory array
     */
    void write_string(String name, int address)
    {
        write_string(name, address, memory);
    }

    /**
     * Writes a java object string to the provided array as
     * Unicode Points. First Integer is chars in String + 1
     * @param name The Java Object string
     * @param address The index of the first integer written
     * @param list The array to be written to
     */
    void write_string(String name, int address, List<Integer> list)
    {
        byte[] b = name.getBytes();
        // length integer
        list.add(address, b.length+1);

        // write Unicode Codepoints
        for(int i = 0; i<b.length; i++){
            list.add(address+i+1, (int)b[i]);
        }
    }

    /**
     * Reads a list of Unicode Points, with the first integer
     * being the length of the string + 1
     * @param address address in the mem array of first integer
     * @return String in java object format
     */
    String read_string(int address)
    {
        if(memory.get(address)-1<0)
            outputln("string length invalid: "+(memory.get(address)-1));

        // read length of string
        byte[] str = new byte[memory.get(address)-1];
        int offset = address+1;

        // read codepoints
        for(int i = 0; i<str.length; i++){
            str[i] = (byte)(int)memory.get(offset + i);
        }

        // convert codepoints to string object
        return new String(str);
    }

    /**
     * Scans the memory and prints each word, in order of
     * declaration, along with its definition
     * Ignores other non-word data, like variable values
     * Should only be used for debugging; assumptions made
     */
    void show_mem()
    {   //make array of word addresses
        List<Integer> pointers = new ArrayList<>();

        // read and store word addresses
        int here = HERE;
        while(here != -1){
            pointers.add(here);
            //move to next word in linked list
            here = memory.get(here-1);
        }

        for(int i = pointers.size()-1;i>=0;i--)
        {
            int word_address = pointers.get(i);

            String word_name = read_string(word_address);
            int immediate = memory.get(word_address + memory.get(word_address));

            output(
                    String.format("%-25s %d %s ", setBoldText + word_name + setPlainText, word_address, immediate==1?"immdt":"     "));

            // first opcode of the word
            int instruction_address = addressToOp(word_address);

            // hard stop for instructions
            // prevent infinite run-on if there is memory corruption
            int nextop = i==0? memory.size():pointers.get(i-1) - 1;

            // iterate through instructions
            for(int j=instruction_address; j<nextop; j++)
            {   // derive name from address
                int mem = memory.get(j);
                String name = read_string(mem);

                //stop iterating if return encountered
                if(name.equals("return"))
                    break;

                output(name + " ");

                // special condition if next element is read as literal
                if(name.equals("literal") || name.equals("branch") || name.equals("branch?"))
                {
                    j++;
                    output(memory.get(j) + " ");
                }
            }
            outputln("");
        }

        outputln("");
    }

    /**
     * Basically an Integer Arraylist but with convenient methods
     */
    public class Stack extends ArrayList<Integer> {
        public int pop(){
            return remove(size()-1);
        }

        public int last(){
            return get(size()-1);
        }

        public void incrementLast(){
            add(pop() + 1);
        }

        public void setLast(int val){
            set(size()-1, val);
        }
    }

    public abstract class scan{
        public boolean forceEnd = false;
        abstract boolean hasNext();
        abstract String next();
    }

    String startup =
            "\n" +
                    ": immediate\n" +
                    "    here read\n" +
                    "    here +\n" +
                    "    1 swap\n" +
                    "    set\n" +
                    ";\n" +
                    "\n" +
                    ": [compile]\n" +
                    "    word stack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": words seemem ;\n" +
                    "\n" +
                    ": [word] word ; immediate\n" +
                    "\n" +
                    ": ll [word] literal [word] literal stack>mem stack>mem ; immediate\n" +
                    "\n" +
                    ": token>stack ll stack>mem word stack>mem ; immediate\n" +
                    "\n" +
                    ": postpone\n" +
                    "    [compile] token>stack\n" +
                    "    token>stack stack>mem stack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": if\n" +
                    "    token>stack branch? stack>mem\n" +
                    "    memposition\n" +
                    "    0 stack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": unless\n" +
                    "    postpone not\n" +
                    "    [compile] if\n" +
                    "; immediate\n" +
                    "\n" +
                    ": then\n" +
                    "    dup\n" +
                    "    memposition swap -\n" +
                    "    swap set\n" +
                    "; immediate\n" +
                    "\n" +
                    ": else\n" +
                    "\ttoken>stack branch stack>mem\n" +
                    "\tmemposition\n" +
                    "\t0 stack>mem\n" +
                    "\tswap\n" +
                    "\tdup\n" +
                    "\tmemposition swap -\n" +
                    "\tswap set\n" +
                    "; immediate\n" +
                    "\n" +
                    ": begin\n" +
                    "\tmemposition\n" +
                    "; immediate\n" +
                    "\n" +
                    ": until\n" +
                    "\ttoken>stack branch? stack>mem\n" +
                    "\tmemposition -\n" +
                    "\tstack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": again\n" +
                    "\ttoken>stack branch stack>mem\n" +
                    "\tmemposition -\n" +
                    "\tstack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": while\n" +
                    "\ttoken>stack branch? stack>mem\n" +
                    "\tmemposition\n" +
                    "\t0 stack>mem\n" +
                    "; immediate\n" +
                    "\n" +
                    ": repeat\n" +
                    "\ttoken>stack branch stack>mem\n" +
                    "\tswap\n" +
                    "\tmemposition - stack>mem\n" +
                    "\tdup\n" +
                    "\tmemposition swap -\n" +
                    "\tswap set\n" +
                    "; immediate\n" +
                    "\n" +
                    ": ) ;\n" +
                    "\n" +
                    ": (\n" +
                    "    [compile] literal [word] ) [ stack>mem ]\n" +
                    "    begin\n" +
                    "        dup word =\n" +
                    "    until\n" +
                    "    drop\n" +
                    "; immediate\n" +
                    "\n" +
                    "( TODO: functionality to nest parentheses )\n" +
                    "\n" +
                    ": constant ( initial_value '' constant_name -- )\n" +
                    "    create              ( set up a new word )\n" +
                    "    stringliteral\n" +
                    "    0 stack>mem\n" +
                    "\n" +
                    "    postpone literal    ( add literal instruction to variable definition )\n" +
                    "    stack>mem           ( append initial value to memory )\n" +
                    "    postpone return     ( add return instruction to constant definition )\n" +
                    ";\n" +
                    "\n" +
                    ": = constant ; ( just thought it made sense )\n" +
                    "\n" +
                    ": variable ( initial_value '' variable_name -- )\n" +
                    "    memposition         ( push memory address to stack )\n" +
                    "    swap\n" +
                    "    memposition set     ( append top of stack to memory )\n" +
                    "\n" +
                    "    create              ( set up a new word )\n" +
                    "    stringliteral\n" +
                    "    0 stack>mem\n" +
                    "\n" +
                    "    postpone literal    ( add literal instruction to variable definition )\n" +
                    "    stack>mem           ( append pointer to memory )\n" +
                    "    postpone return     ( add return instruction to variable definition )\n" +
                    ";\n" +
                    "\n" +
                    ": iftest if 22 print else 11 print then ;\n" +
                    "\n" +
                    ": whiletest begin 11 print 1 until ;\n" +
                    "\n" +
                    ": unlesstest unless 11 print else 22 print then ;\n" +
                    "\n" +
                    "0 unlesstest\n" +
                    "1 unlesstest\n" +
                    "\n" +
                    "0 iftest\n" +
                    "1 iftest\n" +
                    "\n" +
                    "whiletest\n" +
                    "\n" +
                    ": trojan-print postpone print ; immediate\n" +
                    "\n" +
                    ": troy 22 trojan-print 11 print ;\n" +
                    "\n" +
                    "troy\n" +
                    "\n" +
                    "22 print ( 55 print ) 11 print\n" +
                    "\n" +
                    "( commentation! ) ( exciting! )\n" +
                    "\n" +
                    "( cannot nest parentheses )\n" +
                    "\n" +
                    "( You can enable aggressive error messages with the word 'profanity' )\n" +
                    "\n" +
                    "22 constant burgers\n" +
                    "burgers print\n" +
                    "\n" +
                    "11 variable pies\n" +
                    "pies read print\n" +
                    "\n" +
                    "22 pies set\n" +
                    "pies read print\n" +
                    "\n";
}
