/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the line and file mappings associated with a JSR-045 "stratum".
 *
 * @author Jayson Falkner
 * @author Shawn Bayern
 */
public class SmapStratum {

    /**
     * Represents a single LineSection in an SMAP, associated with a particular stratum.
     */
    public static class LineInfo {
        private int inputStartLine = -1;
        private int outputStartLine = -1;
        private int lineFileID = 0;
        private int inputLineCount = 1;
        private int outputLineIncrement = 1;
        private boolean lineFileIDSet = false;

        public void setInputStartLine(int inputStartLine) {
            if (inputStartLine < 0) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.negativeParameter", Integer.valueOf(inputStartLine)));
            }
            this.inputStartLine = inputStartLine;
        }

        public void setOutputStartLine(int outputStartLine) {
            if (outputStartLine < 0) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.negativeParameter", Integer.valueOf(outputStartLine)));
            }
            this.outputStartLine = outputStartLine;
        }

        /**
         * Sets lineFileID. Should be called only when different from that of prior LineInfo object (in any given
         * context) or 0 if the current LineInfo has no (logical) predecessor. <code>LineInfo</code> will print this
         * file number no matter what.
         *
         * @param lineFileID The new line file ID
         */
        public void setLineFileID(int lineFileID) {
            if (lineFileID < 0) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.negativeParameter", Integer.valueOf(lineFileID)));
            }
            this.lineFileID = lineFileID;
            this.lineFileIDSet = true;
        }

        public void setInputLineCount(int inputLineCount) {
            if (inputLineCount < 0) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.negativeParameter", Integer.valueOf(inputLineCount)));
            }
            this.inputLineCount = inputLineCount;
        }

        public void setOutputLineIncrement(int outputLineIncrement) {
            if (outputLineIncrement < 0) {
                throw new IllegalArgumentException(
                        Localizer.getMessage("jsp.error.negativeParameter", Integer.valueOf(outputLineIncrement)));
            }
            this.outputLineIncrement = outputLineIncrement;
        }

        public int getMaxOutputLineNumber() {
            return outputStartLine + inputLineCount * outputLineIncrement;
        }

        /**
         * @return the current LineInfo as a String, print all values only when appropriate (but LineInfoID if and only
         *             if it's been specified, as its necessity is sensitive to context).
         */
        public String getString() {
            if (inputStartLine == -1 || outputStartLine == -1) {
                throw new IllegalStateException();
            }
            StringBuilder out = new StringBuilder();
            out.append(inputStartLine);
            if (lineFileIDSet) {
                out.append("#").append(lineFileID);
            }
            if (inputLineCount != 1) {
                out.append(",").append(inputLineCount);
            }
            out.append(":").append(outputStartLine);
            if (outputLineIncrement != 1) {
                out.append(",").append(outputLineIncrement);
            }
            out.append('\n');
            return out.toString();
        }

        @Override
        public String toString() {
            return getString();
        }
    }


    private final List<String> fileNameList = new ArrayList<>();
    private final List<String> filePathList = new ArrayList<>();
    private final List<LineInfo> lineData = new ArrayList<>();
    private int lastFileID;
    // .java file
    private String outputFileName;
    // .class file
    private String classFileName;


    /**
     * Adds record of a new file, by filename.
     *
     * @param filename the filename to add, unqualified by path.
     */
    public void addFile(String filename) {
        addFile(filename, filename);
    }

    /**
     * Adds record of a new file, by filename and path. The path may be relative to a source compilation path.
     *
     * @param filename the filename to add, unqualified by path
     * @param filePath the path for the filename, potentially relative to a source compilation path
     */
    public void addFile(String filename, String filePath) {
        int pathIndex = filePathList.indexOf(filePath);
        if (pathIndex == -1) {
            fileNameList.add(filename);
            filePathList.add(filePath);
        }
    }

    /**
     * Combines consecutive LineInfos wherever possible
     */
    public void optimizeLineSection() {

        // Incorporate each LineInfo into the previous LineInfo's outputLineIncrement, if possible
        int i = 0;
        while (i < lineData.size() - 1) {
            LineInfo li = lineData.get(i);
            LineInfo liNext = lineData.get(i + 1);
            if (!liNext.lineFileIDSet && liNext.inputStartLine == li.inputStartLine && liNext.inputLineCount == 1 &&
                    li.inputLineCount == 1 &&
                    liNext.outputStartLine == li.outputStartLine + li.inputLineCount * li.outputLineIncrement) {
                li.setOutputLineIncrement(liNext.outputStartLine - li.outputStartLine + liNext.outputLineIncrement);
                lineData.remove(i + 1);
            } else {
                i++;
            }
        }

        // Incorporate each LineInfo into the previous LineInfo's inputLineCount, if possible
        i = 0;
        while (i < lineData.size() - 1) {
            LineInfo li = lineData.get(i);
            LineInfo liNext = lineData.get(i + 1);
            if (!liNext.lineFileIDSet && liNext.inputStartLine == li.inputStartLine + li.inputLineCount &&
                    liNext.outputLineIncrement == li.outputLineIncrement &&
                    liNext.outputStartLine == li.outputStartLine + li.inputLineCount * li.outputLineIncrement) {
                li.setInputLineCount(li.inputLineCount + liNext.inputLineCount);
                lineData.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /**
     * Adds complete information about a simple line mapping. Specify all the fields in this method; the back-end
     * machinery takes care of printing only those that are necessary in the final SMAP. (My view is that fields are
     * optional primarily for spatial efficiency, not for programmer convenience. Could always add utility methods
     * later.)
     *
     * @param inputStartLine      starting line in the source file (SMAP <code>InputStartLine</code>)
     * @param inputFileName       the filepath (or name) from which the input comes (yields SMAP
     *                                <code>LineFileID</code>) Use unqualified names carefully, and only when they
     *                                uniquely identify a file.
     * @param inputLineCount      the number of lines in the input to map (SMAP <code>LineFileCount</code>)
     * @param outputStartLine     starting line in the output file (SMAP <code>OutputStartLine</code>)
     * @param outputLineIncrement number of output lines to map to each input line (SMAP
     *                                <code>OutputLineIncrement</code>). <i>Given the fact that the name starts with
     *                                "output", I continuously have the subconscious urge to call this field
     *                                <code>OutputLineExcrement</code>.</i>
     */
    public void addLineData(int inputStartLine, String inputFileName, int inputLineCount, int outputStartLine,
            int outputLineIncrement) {
        // check the input - what are you doing here??
        int fileIndex = filePathList.indexOf(inputFileName);
        if (fileIndex == -1) {
            throw new IllegalArgumentException("inputFileName: " + inputFileName);
        }

        /*
         * Jasper incorrectly SMAPs certain Nodes, giving them an outputStartLine of 0. This can cause a fatal error in
         * optimizeLineSection, making it impossible for Jasper to compile the JSP. Until we can fix the underlying
         * SMAPping problem, we simply ignore the flawed SMAP entries.
         */
        if (outputStartLine == 0) {
            return;
        }

        // build the LineInfo
        LineInfo li = new LineInfo();
        li.setInputStartLine(inputStartLine);
        li.setInputLineCount(inputLineCount);
        li.setOutputStartLine(outputStartLine);
        li.setOutputLineIncrement(outputLineIncrement);
        if (fileIndex != lastFileID) {
            li.setLineFileID(fileIndex);
        }
        lastFileID = fileIndex;

        // save it
        lineData.add(li);
    }


    public void addLineInfo(LineInfo li) {
        lineData.add(li);
    }


    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
    }


    public void setClassFileName(String classFileName) {
        this.classFileName = classFileName;
    }


    public String getClassFileName() {
        return classFileName;
    }


    @Override
    public String toString() {
        return getSmapStringInternal();
    }


    public String getSmapString() {

        if (outputFileName == null) {
            throw new IllegalStateException();
        }

        return getSmapStringInternal();
    }


    private String getSmapStringInternal() {
        StringBuilder out = new StringBuilder();

        // start the SMAP
        out.append("SMAP\n");
        out.append(outputFileName).append('\n');
        out.append("JSP\n");

        // print StratumSection
        out.append("*S JSP\n");

        // print FileSection
        out.append("*F\n");
        int bound = fileNameList.size();
        for (int i = 0; i < bound; i++) {
            if (filePathList.get(i) != null) {
                out.append("+ ").append(i).append(" ").append(fileNameList.get(i)).append("\n");
                // Source paths must be relative, not absolute, so we
                // remove the leading "/", if one exists.
                String filePath = filePathList.get(i);
                if (filePath.startsWith("/")) {
                    filePath = filePath.substring(1);
                }
                out.append(filePath).append("\n");
            } else {
                out.append(i).append(" ").append(fileNameList.get(i)).append("\n");
            }
        }

        // print LineSection
        out.append("*L\n");
        bound = lineData.size();
        for (int i = 0; i < bound; i++) {
            LineInfo li = lineData.get(i);
            out.append(li.getString());
        }

        // end the SMAP
        out.append("*E\n");

        return out.toString();
    }


    public SmapInput getInputLineNumber(int outputLineNumber) {
        // For a given Java line number, provide the associated line number
        // in the JSP/tag source
        int inputLineNumber = -1;
        int fileId = 0;

        for (LineInfo lineInfo : lineData) {
            if (lineInfo.lineFileIDSet) {
                fileId = lineInfo.lineFileID;
            }
            if (lineInfo.outputStartLine > outputLineNumber) {
                // Didn't find match
                break;
            }

            if (lineInfo.getMaxOutputLineNumber() < outputLineNumber) {
                // Too early
                continue;
            }

            // This is the match
            int inputOffset = (outputLineNumber - lineInfo.outputStartLine) / lineInfo.outputLineIncrement;

            inputLineNumber = lineInfo.inputStartLine + inputOffset;
        }

        return new SmapInput(filePathList.get(fileId), inputLineNumber);
    }
}
