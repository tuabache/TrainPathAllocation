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

package ch.oakmountain.tpa.solver;

import ch.oakmountain.tpa.web.IMatrix;
import ch.oakmountain.tpa.web.IMatrixPersistor;
import gurobi.*;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class TrainPathAllocationProblemModelStatistics implements IMatrix {

    private GRBModel grbModel;
    private TrainPathAllocationProblemModel model;
    private List<String> varNames;

    public TrainPathAllocationProblemModelStatistics(GRBModel grbModel, TrainPathAllocationProblemModel model) throws GRBException {
        this.grbModel = grbModel;
        this.model = model;
        varNames = getVarNamesFromModel();
    }

    @Override
    public List<? extends Object> getNodes() throws GRBException {
        return varNames;
    }

    private List<String> getVarNamesFromModel() throws GRBException {
        List<String> varNames = new LinkedList<>();
        for (GRBVar grbVar : grbModel.getVars()) {
            varNames.add(grbVar.get(GRB.StringAttr.VarName));
        }
        return varNames;
    }

    @Override
    public void getEdges(IMatrixPersistor persistor) throws IOException, GRBException {
        for (GRBConstr constr : grbModel.getConstrs()) {
            String constrName = constr.get(GRB.StringAttr.ConstrName);
            GRBLinExpr row = grbModel.getRow(constr);
            for (int i = 0; i < row.size(); i++) {
                GRBVar var_i = row.getVar(i);
                String var_iName = var_i.get(GRB.StringAttr.VarName);
                int var_iIndex = varNames.indexOf(var_iName);
                for (int j = i + 1; j < row.size(); j++) {
                    GRBVar var_j = row.getVar(i);
                    String var_jName = var_j.get(GRB.StringAttr.VarName);
                    int var_jIndex = varNames.indexOf(var_jName);
                    persistor.appendEdge(var_iIndex, var_jIndex, 1);
                    persistor.appendEdge(var_jIndex, var_iIndex, 1);
                }
            }
        }
    }
}
