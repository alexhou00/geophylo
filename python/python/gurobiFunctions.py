import dotenv
import numpy as np
import scipy.sparse as sp
import gurobipy as gp
from gurobipy import GRB
import os

usingLicense = False
dotenv.load_dotenv()
if (
    (os.getenv("GRB_WLSACCESSID") is not None)
    and (os.getenv("GRB_LICENSEID") is not None)
    and (os.getenv("GRB_WLSSECRET") is not None)
):
    print("\nLICENSE INFO: usingLicense\n")
    usingLicense = True
    gurobiEnv = gp.Env(empty=True)

    wlsaccessID = os.getenv("GRB_WLSACCESSID", "undefined")
    gurobiEnv.setParam("WLSACCESSID", wlsaccessID)

    licenseID = os.getenv("GRB_LICENSEID", "0")
    gurobiEnv.setParam("LICENSEID", int(licenseID))

    wlsSecrets = os.getenv("GRB_WLSSECRET", "undefined")
    gurobiEnv.setParam("WLSSECRET", wlsSecrets)

    gurobiEnv.setParam("CSCLIENTLOG", int(3))

    gurobiEnv.start()
else: 
    print("\nLICENSE INFO: not using license\n")


def giveMinLeaderIntersectConfig(self):



    intersectingSitePairs = []
    fixedSitePairs = []
    horizontalSitePairs = []

    for i in range(0, len(self.sites)):
        for j in range(i + 1, len(self.sites)):
            if (
                self.lType == "po"
                and abs(self.sites[i].pos[1] - self.sites[j].pos[1]) < self.poGap
            ):
                isSite1Left = self.sites[i].pos[0] < self.sites[j].pos[0]
                horizontalSitePairs.append([self.sites[i], self.sites[j], isSite1Left])
            else:
                (
                    thisIntersectIndex,
                    isSite1Lower,
                ) = self.giveTwoSitesTopLineIntersectIndex(
                    self.sites[i].pos, self.sites[j].pos
                )

                if (
                    thisIntersectIndex > 0
                    and thisIntersectIndex < self.innerVertices[0].subTreeWidth - 1
                ):
                    intersectingSitePairs.append(
                        [
                            self.sites[i],
                            self.sites[j],
                            thisIntersectIndex,
                            isSite1Lower,
                        ]
                    )
                else:
                    fixedSitePairs.append(
                        [
                            self.sites[i],
                            self.sites[j],
                            thisIntersectIndex,
                            isSite1Lower,
                        ]
                    )

    if usingLicense:
        ilpModel = gp.Model(env=gurobiEnv, name="ilpModel")
    else:
        ilpModel = gp.Model("ilpModel")

    innerVerticesVars = ilpModel.addMVar(
        len(self.innerVertices), vtype=GRB.BINARY, name="innerVertices"
    )
    intersectingSitePairsVars = ilpModel.addMVar(
        len(intersectingSitePairs), vtype=GRB.BINARY, name="intersectingSitePairs"
    )
    allowIntersectForFixedVars = ilpModel.addMVar(
        len(fixedSitePairs), vtype=GRB.BINARY, name="allowIntersectForFixed"
    )
    allowIntersectForIntersectingVars = ilpModel.addMVar(
        len(intersectingSitePairs),
        vtype=GRB.BINARY,
        name="allowIntersectForIntersecting",
    )
    allowIntersectForHorizontalVars = ilpModel.addMVar(
        len(horizontalSitePairs),
        vtype=GRB.BINARY,
        name="allowIntersectForHorizontal",
    )

    objective = gp.LinExpr()
    if len(fixedSitePairs) > 0:
        objective += allowIntersectForFixedVars.sum()
    if len(intersectingSitePairs) > 0:
        objective += allowIntersectForIntersectingVars.sum()
    if len(horizontalSitePairs) > 0:
        objective += allowIntersectForHorizontalVars.sum(), GRB.MINIMIZE
    ilpModel.setObjective(objective)

    fixedConstraintsVal = []
    fixedConstraintsCol = []
    fixedConstraintsRhs = []

    for thisSitePair in fixedSitePairs:
        if thisSitePair[0].pos != thisSitePair[1].pos:
            thisLowestCommonParent = self.giveLowestCommonParentVertex(
                thisSitePair[0].leaf, thisSitePair[1].leaf
            )
            thisIntersectIndex = thisSitePair[2]
            isSite1Lower = thisSitePair[3]
            # is leaf 1 in the left subtree?
            isThisLeaf1LeftOf2 = thisLowestCommonParent[1]

            # because the intersect index for fixed Pairs can either be negative
            # (meaning the line drawn from 1 to 2 passes the top line lefthand) or positive (passes righthand)
            # if the intersecting line is reversed (from 2 to 1) the result is reversed to
            isThisSite1LeftOf2 = (thisIntersectIndex > 0) == isSite1Lower

            leafsInOrder = isThisLeaf1LeftOf2 == isThisSite1LeftOf2

            if leafsInOrder:
                fixedConstraintsVal.append(1)
                fixedConstraintsRhs.append(0)
            else:
                fixedConstraintsVal.append(-1)
                fixedConstraintsRhs.append(-1)

            fixedConstraintsCol.append(thisLowestCommonParent[0].totalIndex)

    fixedConstraintsMatrix = sp.csc_matrix(
        (
            np.array(fixedConstraintsVal),
            (np.arange(0, len(fixedSitePairs), 1), np.array(fixedConstraintsCol)),
        ),
        shape=(len(fixedSitePairs), len(self.innerVertices)),
    )
    fixedConstrainsRhsVector = np.array(fixedConstraintsRhs)

    if len(fixedSitePairs) > 0:
        ilpModel.addConstr(
            fixedConstraintsMatrix @ innerVerticesVars - allowIntersectForFixedVars
            <= fixedConstrainsRhsVector,
            name="fixedConstraints",
        )

    intersectingInnerVerticesVal = []
    intersectingInnerVerticesRow = []
    intersectingInnerVerticesCol = []
    intersectingRhs = []

    intersectingSitePairsVal = []
    intersectingSitePairsCol = []

    bigMVal = self.innerVertices[0].subTreeWidth
    bigNVal = bigMVal * 2

    for i in range(0, len(intersectingSitePairs)):
        thisSitePair = intersectingSitePairs[i]

        if thisSitePair[0].pos != thisSitePair[1].pos:
            thisIntersectIndex = thisSitePair[2]
            isSite1Lower = thisSitePair[3]

            if isSite1Lower:
                lowerSite = thisSitePair[0]
                upperSite = thisSitePair[1]
            else:
                lowerSite = thisSitePair[1]
                upperSite = thisSitePair[0]

            thisLowestCommonParent = self.giveLowestCommonParentVertex(
                lowerSite.leaf, upperSite.leaf
            )

            # Big M and N Values for case 1 and 2
            for j in range(0, 2):
                intersectingSitePairsCol.append(i)
                intersectingSitePairsVal.append(-bigMVal)
            for j in range(0, 2):
                intersectingSitePairsCol.append(i)
                intersectingSitePairsVal.append(bigMVal)

            # case 1: lowerSite passing upperSite left hand
            # case 1 constraint 1: lowerSite left of intersect
            for j in range(0, len(lowerSite.leaf.parentCoef)):
                intersectingInnerVerticesVal.append(lowerSite.leaf.parentCoef[j])
                intersectingInnerVerticesRow.append(4 * i)
                intersectingInnerVerticesCol.append(
                    lowerSite.leaf.allParents[j][0].totalIndex
                )

            intersectingRhs.append(thisIntersectIndex - lowerSite.leaf.initialOffset)

            # case 1 constraint 2: lowerSite left of upperSite
            intersectingInnerVerticesRow.append(4 * i + 1)
            intersectingInnerVerticesCol.append(thisLowestCommonParent[0].totalIndex)

            if thisLowestCommonParent[1]:
                # lowerSite is initially left of upperSite so parent should NOT turn
                intersectingInnerVerticesVal.append(1)
                intersectingRhs.append(0)
            else:
                # lowerSite is initially right of upperSite so parent should turn
                intersectingInnerVerticesVal.append(-1)
                intersectingRhs.append(-1)

            # case 2: lowerSite passing upperSite right hand
            # case 2 constraint 1: lowerSite right of intersect
            for j in range(0, len(lowerSite.leaf.parentCoef)):
                intersectingInnerVerticesVal.append(-lowerSite.leaf.parentCoef[j])
                intersectingInnerVerticesRow.append(4 * i + 2)
                intersectingInnerVerticesCol.append(
                    lowerSite.leaf.allParents[j][0].totalIndex
                )

            intersectingRhs.append(
                -thisIntersectIndex + lowerSite.leaf.initialOffset + bigMVal
            )

            # case 2 constraint 2: lowerSite right of upperSite
            intersectingInnerVerticesRow.append(4 * i + 3)
            intersectingInnerVerticesCol.append(thisLowestCommonParent[0].totalIndex)

            if thisLowestCommonParent[1]:
                # lowerSite is initially left of upperSite so parent should turn
                intersectingInnerVerticesVal.append(-1)
                intersectingRhs.append(-1 + bigMVal)
            else:
                # lowerSite is initially right of upperSite so parent should NOT turn
                intersectingInnerVerticesVal.append(1)
                intersectingRhs.append(bigMVal)

    intersectingInnerVerticesMatrix = sp.csc_matrix(
        (
            np.array(intersectingInnerVerticesVal),
            (
                np.array(intersectingInnerVerticesRow),
                np.array(intersectingInnerVerticesCol),
            ),
        ),
        shape=(len(intersectingSitePairs) * 4, len(self.innerVertices)),
    )
    intersectingSitePairsMatrix = sp.csc_matrix(
        (
            np.array(intersectingSitePairsVal),
            (
                np.arange(0, len(intersectingSitePairsVal), 1),
                np.array(intersectingSitePairsCol),
            ),
        ),
        shape=(len(intersectingSitePairs) * 4, len(intersectingSitePairs)),
    )
    intersectingBigNMatrix = sp.csc_matrix(
        (
            np.full(len(intersectingSitePairs) * 4, bigNVal),
            (
                np.arange(0, len(intersectingSitePairsVal), 1),
                np.array(intersectingSitePairsCol),
            ),
        ),
        shape=(len(intersectingSitePairs) * 4, len(intersectingSitePairs)),
    )
    intersectingRhsVector = np.array(intersectingRhs)

    if len(intersectingSitePairs) > 0:
        ilpModel.addConstr(
            intersectingInnerVerticesMatrix @ innerVerticesVars
            + intersectingSitePairsMatrix @ intersectingSitePairsVars
            - intersectingBigNMatrix @ allowIntersectForIntersectingVars
            <= intersectingRhsVector,
            name="intersectingConstraints",
        )

    horizontalInnerVerticesVal = []
    horizontalInnerVerticesRow = []
    horizontalInnerVerticesCol = []
    horizontalSitePairsCol = []
    horizontalRhs = []

    for i in range(0, len(horizontalSitePairs)):
        thisSitePair = horizontalSitePairs[i]

        isSite1Left = thisSitePair[2]

        for j in range(0, 3):
            horizontalSitePairsCol.append(i)

        if isSite1Left:
            leftSite = thisSitePair[0]
            rightSite = thisSitePair[1]
        else:
            leftSite = thisSitePair[1]
            rightSite = thisSitePair[0]

        thisLowestCommonParent = self.giveLowestCommonParentVertex(
            leftSite.leaf, rightSite.leaf
        )

        # constraint 1: left leaf left of right site
        rightSiteIndex = (
            (rightSite.pos[0] - self.topLineStart[0])
            / (self.topLineEnd[0] - self.topLineStart[0])
            * (self.innerVertices[0].subTreeWidth - 1)
        )
        for j in range(0, len(leftSite.leaf.parentCoef)):
            horizontalInnerVerticesVal.append(leftSite.leaf.parentCoef[j])
            horizontalInnerVerticesRow.append(3 * i)
            horizontalInnerVerticesCol.append(leftSite.leaf.allParents[j][0].totalIndex)

        horizontalRhs.append(rightSiteIndex - leftSite.leaf.initialOffset)

        # constraint 2: right leaf right of left site
        leftSiteIndex = (
            (leftSite.pos[0] - self.topLineStart[0])
            / (self.topLineEnd[0] - self.topLineStart[0])
            * (self.innerVertices[0].subTreeWidth - 1)
        )
        for j in range(0, len(rightSite.leaf.parentCoef)):
            horizontalInnerVerticesVal.append(-rightSite.leaf.parentCoef[j])
            horizontalInnerVerticesRow.append(3 * i + 1)
            horizontalInnerVerticesCol.append(
                rightSite.leaf.allParents[j][0].totalIndex
            )

        horizontalRhs.append(-leftSiteIndex + rightSite.leaf.initialOffset)

        # constraint 3: left leaf left of right leaf
        horizontalInnerVerticesRow.append(3 * i + 2)
        horizontalInnerVerticesCol.append(thisLowestCommonParent[0].totalIndex)

        if thisLowestCommonParent[1]:
            # leftSite is initially left of rightSite so parent should NOT turn
            horizontalInnerVerticesVal.append(1)
            horizontalRhs.append(0)
        else:
            # leftSite is initially right of upperSite so parent should turn
            horizontalInnerVerticesVal.append(-1)
            horizontalRhs.append(-1)

    horizontalInnerVerticesMatrix = sp.csc_matrix(
        (
            np.array(horizontalInnerVerticesVal),
            (
                np.array(horizontalInnerVerticesRow),
                np.array(horizontalInnerVerticesCol),
            ),
        ),
        shape=(len(horizontalSitePairs) * 3, len(self.innerVertices)),
    )
    horizontalBigNMatrix = sp.csc_matrix(
        (
            np.full(len(horizontalSitePairs) * 3, bigNVal),
            (
                np.arange(0, len(horizontalSitePairs) * 3, 1),
                np.array(horizontalSitePairsCol),
            ),
        ),
        shape=(len(horizontalSitePairs) * 3, len(horizontalSitePairs)),
    )
    horizontalRhsVector = np.array(horizontalRhs)

    if len(horizontalSitePairs) > 0:
        ilpModel.addConstr(
            horizontalInnerVerticesMatrix @ innerVerticesVars
            - horizontalBigNMatrix @ allowIntersectForHorizontalVars
            <= horizontalRhsVector,
            name="horizontalConstraints",
        )

    ilpModel.optimize()

    res = [[], ilpModel.ObjVal]

    for thisInnerVertexIndex in range(len(self.innerVertices)):
        res[0].append(
            [
                self.innerVertices[thisInnerVertexIndex].id,
                bool(innerVerticesVars.X[thisInnerVertexIndex]),
            ]
        )

    return res
