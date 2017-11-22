/*
 * Copyright (c)  2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.execution.reorder;

import org.apache.log4j.Logger;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The following code conducts reordering of an out-of-order event stream.
 * This implements the elimination of Duplicates.
 */
@Extension(
        name = "reorder",
        namespace = "duplicate",
        description = "This stream processor extension performs reordering of an out-of-order event stream by "
                + "putting a sequential Number for the newly created joined Events"
                + ".\n",
        parameters = {
                @Parameter(name = "serial.no",
                           description = "Attribute used for ordering the events",
                           type = {DataType.DOUBLE, DataType.STRING})
        },
        examples = @Example(
                syntax = "define stream inputStream (serialNo double, price long, volume long);\n" +
                        "@info(name = 'query1')\n" +
                        "from inputStream#reorder:duplicate(serialNo)\n" +
                        "select serialNo, price, volume\n" +
                        "insert into outputStream;",
                description = "This query performs reordering based on the 'serialNo' attribute value")
)
public class DuplicateExtension extends StreamProcessor {

    private static final Logger log = Logger.getLogger(DuplicateExtension.class);
    private ExpressionExecutor serialNoExecutor;
    private double times = 0;
    private double lastSentSerialNo = 0.0;

    @Override
    public void start() {
        //Do nothing
    }

    @Override
    public void stop() {
        //Do nothing
    }

    @Override
    public Map<String, Object> currentState() {
        return new HashMap<String, Object>();
    }

    @Override public void restoreState(Map<String, Object> map) {

    }

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {

        while (streamEventChunk.hasNext()) {

            ComplexEventChunk<StreamEvent> complexEventChunk = new ComplexEventChunk<StreamEvent>(false);
            StreamEvent event = streamEventChunk.next();

            synchronized (this) {

                streamEventChunk.remove();
                //We might have the rest of the events linked to this event forming a chain.

                double serialNo;
                Object[] data = event.getOutputData();
                if (data[0] == null) {
                    times++;
                    serialNo = lastSentSerialNo + (0.000001 * times);
                } else {
                    serialNo = (Double) serialNoExecutor.execute(event);
                    double difference = (serialNo - lastSentSerialNo);
                    if (difference == 0) {
                        times++;
                        serialNo = lastSentSerialNo + (0.000001 * times);

                    } else if (serialNo < lastSentSerialNo) {
                        times++;
                        serialNo = lastSentSerialNo + (0.000001 * times);
                    } else {
                        times = 0;
                        lastSentSerialNo = serialNo;

                    }
                }

                data[0] = serialNo;
                event.setOutputData(data);
                complexEventChunk.add(event);
                nextProcessor.process(complexEventChunk);
            }

        }
    }

    @Override
    protected List<Attribute> init(AbstractDefinition abstractDefinition, ExpressionExecutor[] expressionExecutors,
                                   ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
        ArrayList<Attribute> attributes = new ArrayList<Attribute>();

        if (attributeExpressionLength > 1) {
            throw new SiddhiAppCreationException("Maximum four input parameters can be specified for duplicate. " +
                                                         " SerialNo field (double). But found "
                                                         +
                                                         attributeExpressionLength + " attributes.");
        }

        if (attributeExpressionExecutors.length == 1) {
            if (attributeExpressionExecutors[0].getReturnType() == Attribute.Type.DOUBLE) {
                serialNoExecutor = attributeExpressionExecutors[0];
                attributes.add(new Attribute("beta0", Attribute.Type.DOUBLE));
            } else if (attributeExpressionExecutors[0].getReturnType() == Attribute.Type.STRING) {
                serialNoExecutor = attributeExpressionExecutors[0];
                attributes.add(new Attribute("beta0", Attribute.Type.STRING));
            } else {
                throw new SiddhiAppCreationException("Invalid parameter type found for the first argument of " +
                                                             "reorder:duplicate() function. Required DOUBLE, but found "
                                                             +
                                                             attributeExpressionExecutors[0].getReturnType());
            }
        }

        if (attributeExpressionExecutors[0].getReturnType() == Attribute.Type.DOUBLE) {
            serialNoExecutor = attributeExpressionExecutors[0];
        } else if (attributeExpressionExecutors[0].getReturnType() == Attribute.Type.STRING) {
            serialNoExecutor = attributeExpressionExecutors[0];
        } else {
            throw new SiddhiAppCreationException("Return type expected by duplicate is LONG but found " +
                                                         attributeExpressionExecutors[0].getReturnType());
        }

        return attributes;
    }
}
