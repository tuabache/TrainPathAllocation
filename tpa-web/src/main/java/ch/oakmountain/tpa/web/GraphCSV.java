/*
 * Copyright 2016 Christian Eichenberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.oakmountain.tpa.web;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class GraphCSV {
    Set<String> lines = new HashSet<>();

    /* category:
       1 : grey directed edge
       2 : red directed edge
       3 : green directed edge
       4 : grey undirected edge
     */


    public void appendLine(String source, String target, String category, String linkDescription, String sourceGroup, String targetGroup, String sourceGroupDescription, String targetGroupDescription) {
        String line = "\"" + source + "\",\"" + target + "\",\"" + category + "\",\"" + linkDescription + "\",\"" + sourceGroup + "\",\"" + targetGroup + "\",\"" + sourceGroupDescription + "\",\"" + targetGroupDescription + "\"";
        lines.add(line);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("source,target,category,linkDescription,sourceGroup,targetGroup,sourceGroupDescription,targetGroupDescription" + System.lineSeparator());
        for (String line : lines) {
            builder.append(line).append(System.lineSeparator());
        }
        return builder.toString();
    }


}
