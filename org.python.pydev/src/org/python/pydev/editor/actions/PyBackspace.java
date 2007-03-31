/*
 * @author: Fabio Zadrozny
 * Created: July 2004
 * License: Common Public License v1.0
 */
package org.python.pydev.editor.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.python.pydev.core.docutils.PySelection;
import org.python.pydev.editor.autoedit.DefaultIndentPrefs;
import org.python.pydev.editor.autoedit.IIndentPrefs;

/**
 * @author Fabio Zadrozny
 * 
 * Makes a backspace happen...
 * 
 * We can:
 * - go to the indentation from some uncommented previous line (if we
 *   only have whitespaces in the current line).
 * - erase all whitespace characters until we find some character.
 * - erase a single character.
 */
public class PyBackspace extends PyAction {

    private IIndentPrefs prefs;
    
    public void setIndentPrefs(IIndentPrefs prefs) {
        this.prefs = prefs;
    }

    public IIndentPrefs getIndentPrefs() {
        if (this.prefs == null) {
            this.prefs = new DefaultIndentPrefs(); //create the default if this is still not done.
        }
        return this.prefs;
    }
    
    public void perform(PySelection ps) {
        // Perform the action
        try {
            ITextSelection textSelection = ps.getTextSelection();

            if (textSelection.getLength() != 0) {
                eraseSelection(ps);
                return;
            }

            int lastCharPosition = getLastCharPosition(ps.getDoc(), ps.getLineOffset());

            int cursorOffset = textSelection.getOffset();

            IRegion lastCharRegion = ps.getDoc().getLineInformationOfOffset(lastCharPosition + 1);
            //System.out.println("cursorOffset: "+ cursorOffset);
            //System.out.println("lastCharPosition: "+lastCharPosition);
            //System.out.println("lastCharRegion.getOffset(): "
            // +lastCharRegion.getOffset());
            //          IRegion cursorRegion =
            // ps.doc.getLineInformationOfOffset(cursorOffset);

            if (cursorOffset == lastCharRegion.getOffset()) {
                //System.out.println("We are in the beggining of the line.");
                //in this situation, we are in the first character of the
                // line...
                //so, we have to get the end of the other line and delete it.
                if (cursorOffset != 0) //we only want to erase if we are not in
                                       // the first line.
                    eraseLineDelimiter(ps);
            } else if (cursorOffset <= lastCharPosition) {
                //System.out.println("cursorOffset <= lastCharPosition");
                //this situation is:
                //    |a (delete to previous indentation - considers cursor
                // position)
                //or
                //    as | as (delete single char)
                //or
                //  | a (delete to previous indentation - considers cursor
                // position)
                //so, we have to treat it carefully
                eraseToPreviousIndentation(ps, false);
            } else if (lastCharRegion.getOffset() == lastCharPosition + 1) {
                //System.out.println("Only whitespaces in the line.");
                //in this situation, this line only has whitespaces,
                //so, we have to erase depending on the previous indentation.
                eraseToPreviousIndentation(ps, true);
            } else {

                if (cursorOffset - lastCharPosition == 1) {
                    //System.out.println("Erase single char.");
                    //last char and cursor are in the same line.
                    //this situation is:
                    //    a|
                    eraseSingleChar(ps);

                } else if (cursorOffset - lastCharPosition > 1) {
                    //this situation is:
                    //    a |
                    //System.out.println("Erase until last char is found.");
                    eraseUntilLastChar(ps, lastCharPosition);
                }
            }
        } catch (Exception e) {
            beep(e);
        }
    }

    
    /**
     * Makes a backspace happen...
     * 
     * We can:
     * - go to the indentation from some uncommented previous line (if
     *   we only have whitespaces in the current line).
     *  - erase all whitespace characters until we find some character.
     *  - erase a single character.
     */
    public void run(IAction action) {
    	OfflineActionTarget adapter = (OfflineActionTarget) getPyEdit().getAdapter(OfflineActionTarget.class);
    	if(adapter != null){
    		if(adapter.isInstalled()){
    			adapter.removeLastCharSearchAndUpdateStatus();
    			return;
    		}
    	}
        PySelection ps = new PySelection(getTextEditor());
        perform(ps);
    }

    /**
     * @param ps
     * @param hasOnlyWhitespaces
     * @throws BadLocationException
     */
    private void eraseToPreviousIndentation(PySelection ps, boolean hasOnlyWhitespaces)
            throws BadLocationException {
        ITextSelection textSelection = ps.getTextSelection();
        int indentation = getPreviousIndentation(ps, textSelection.getStartLine());
        if (indentation == -1) {
            //System.out.println("erasing single char");
            eraseSingleChar(ps);
        } else {
            if (hasOnlyWhitespaces) {
                //System.out.println("only whitespaces");
                eraseToIndentation(ps, indentation);
            } else {
                //System.out.println("not only whitespaces");
                //this situation is:
                //    |a (delete to previous indentation - considers cursor position)
                //
            	//or
            	//
                //    as | as (delete single char)
            	//
                //so, we have to treat it carefully
                //TODO: use the conditions above and not just erase a single
                // char.

            	if(PySelection.containsOnlyWhitespaces(ps.getLineContentsToCursor())){
            		eraseToIndentation(ps, indentation);
            		
            	}else{
            		eraseSingleChar(ps);
            	}
            }
        }
    }

    /**
     * 
     * @param ps
     * @param textSelection
     * @return offset of the indentation on the previous non-commented line or
     *         -1 if we are not able to get it (if this happens, we delete 1
     *         char).
     * @throws BadLocationException
     */
    private static int getPreviousIndentation(PySelection ps, int currentLine) throws BadLocationException {
        if (currentLine == 0) {
            //we are in the first line, so, we have no basis to get
            // indentation.
            return -1;
        } else {
            for (int i = currentLine - 1; i >= 0; i++) {
                int currentLineOffset = ps.getDoc().getLineOffset(i);
                if (ps.getDoc().getChar(currentLineOffset + 1) != '#') {
                    int currentLineFirstCharPos = PySelection.getFirstCharRelativePosition(ps.getDoc(), currentLineOffset);
                    return currentLineFirstCharPos;
                }
            }
        }
        return -1;
    }

    /**
     * 
     * @param ps
     * @throws BadLocationException
     */
    private void eraseSingleChar(PySelection ps) throws BadLocationException {
        ITextSelection textSelection = ps.getTextSelection();

        ps.getDoc().replace(textSelection.getOffset() - 1, 1, "");
    }

    /**
     * 
     * @param ps
     * @throws BadLocationException
     */
    private void eraseLineDelimiter(PySelection ps) throws BadLocationException {

        ITextSelection textSelection = ps.getTextSelection();

        int length = getDelimiter(ps.getDoc()).length();
        int offset = textSelection.getOffset() - length;

        //System.out.println("Replacing offset: "+(offset) +" lenght: "+
        // (length));
        ps.getDoc().replace(offset, length, "");
    }

    /**
     * 
     * @param ps
     * @throws BadLocationException
     */
    private void eraseSelection(PySelection ps) throws BadLocationException {
        ITextSelection textSelection = ps.getTextSelection();

        ps.getDoc().replace(textSelection.getOffset(), textSelection.getLength(), "");
    }

    /**
     * @param ps
     * @param lastCharPosition
     * @throws BadLocationException
     */
    private void eraseUntilLastChar(PySelection ps, int lastCharPosition) throws BadLocationException {
        ITextSelection textSelection = ps.getTextSelection();
        int cursorOffset = textSelection.getOffset();

        int offset = lastCharPosition + 1;
        int length = cursorOffset - lastCharPosition - 1;
        //System.out.println("Replacing offset: "+(offset) +" lenght: "+
        // (length));
        ps.getDoc().replace(offset, length, "");
    }

    /**
     * TODO: Make use of the indentation gotten previously. This implementation
     * just uses the indentation string and erases the number of chars from it.
     * 
     * @param ps
     * @param indentation this is in number of characters.
     * @throws BadLocationException
     */
    private void eraseToIndentation(PySelection ps, int indentation) throws BadLocationException {
        ITextSelection textSelection = ps.getTextSelection();
        int cursorOffset = textSelection.getOffset();

        String indentationString = prefs.getIndentationString();
        int length = indentationString.length();
        int offset = cursorOffset - length;

        IRegion region = ps.getDoc().getLineInformationOfOffset(cursorOffset);
        int dif = region.getOffset() - offset;
        //System.out.println("dif = "+dif);
        if (dif > 0) {
            offset += dif;
            length -= dif;
        }
        //we have to be careful not to erase more than the current line.
        //System.out.println("Replacing offset: "+(offset) +" lenght: "+
        // (length));
        ps.getDoc().replace(offset, length, "");
    }




}