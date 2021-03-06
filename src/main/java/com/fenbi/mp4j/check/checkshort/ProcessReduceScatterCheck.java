/**
*
* Copyright (c) 2017 ytk-mp4j https://github.com/yuantiku
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:

* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.

* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package com.fenbi.mp4j.check.checkshort;

import com.fenbi.mp4j.check.ProcessCheck;
import com.fenbi.mp4j.comm.ProcessCommSlave;
import com.fenbi.mp4j.exception.Mp4jException;
import com.fenbi.mp4j.operand.Operands;
import com.fenbi.mp4j.operator.Operators;

import java.util.*;

/**
 * @author xialong
 */
public class ProcessReduceScatterCheck extends ProcessCheck {

    public ProcessReduceScatterCheck(ProcessCommSlave slave, String serverHostName, int serverHostPort, int arrSize, int objSize, int runTime, boolean compress) {
        super(slave, serverHostName, serverHostPort, arrSize, objSize, runTime, compress);
    }

    @Override
    public void check() throws Mp4jException {
        int rank = slave.getRank();
        int slaveNum = slave.getSlaveNum();
        boolean success = true;
        long start;
        short []arr = new short[arrSize];

        for (int rt = 1; rt <= runTime; rt++) {
            info("run time:" + rt + "...");

            // short array
            info("begin to reducescatter short arr...");
            int avgnum = arrSize / slaveNum;

            int from = 0;
            int []counts = new int[slaveNum];

            for (int r = 0; r < slaveNum; r++) {
                counts[r] = avgnum;
            }
            counts[slaveNum - 1] = arrSize - (slaveNum - 1) * avgnum;

            for (int i = 0; i < arrSize; i++) {
                int r = avgnum == 0 ? slaveNum - 1 : i / avgnum;
                arr[i] = (short)r;
            }
            start = System.currentTimeMillis();
            slave.reduceScatterArray(arr, Operands.SHORT_OPERAND(compress), Operators.Short.SUM, from, counts);
            info("reducescatter short arr takes:" + (System.currentTimeMillis() - start));

            int startidx = rank * avgnum;
            int endidx = startidx + avgnum;
            if (rank == slaveNum - 1) {
                endidx = arrSize;
            }
            for (int i = startidx; i < endidx; i++) {
                int r = avgnum == 0 ? slaveNum - 1 : i / avgnum;
                if (arr[i] != r * slaveNum) {
                    info("reducescatter short array error:" + Arrays.toString(arr), false);
                    slave.close(1);
                }
            }
            info("reducescatter short arr success!");
            if (arrSize < 500) {
                info("reducescatter result:" + Arrays.toString(arr));
            }

            // map
            info("begin to reducescatter short map...");
            List<Map<String, Short>> mapList = new ArrayList<>(slaveNum);
            for (int r = 0; r < slaveNum; r++) {
                Map<String, Short> map = new HashMap<>(objSize);
                mapList.add(map);
                for (int i = r * objSize; i < (r + 1) * objSize; i++) {
                    map.put(i + "", new Short((short)1));
                }
            }

            start = System.currentTimeMillis();
            Map<String, Short> retMap = slave.reduceScatterMap(mapList, Operands.SHORT_OPERAND(compress), Operators.Short.SUM);
            info("reducescatter short map takes:" + (System.currentTimeMillis() - start));

            success = true;
            if (retMap.size() != objSize) {
                info("reducescatter short map retMap size:" + retMap.size() + ", expected size:" + objSize);
                success = false;
            }

            for (int i = rank * objSize; i < (rank + 1) * objSize; i++) {
                Short val = retMap.get(i + "");
                if (val == null || val.intValue() != slaveNum) {
                    success = false;
                }
            }

            if (!success) {
                info("reducescatter short map error:" + retMap);
                slave.close(1);
            }

            if (objSize < 500) {
                info("reducescatter short map:" + retMap);
            }
            info("reducescatter short map success!");
        }

    }
}
