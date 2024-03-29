/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.test.check.registrycenter;

import org.apache.dubbo.test.check.exception.DubboTestException;
import org.apache.dubbo.test.check.registrycenter.context.ZookeeperContext;
import org.apache.dubbo.test.check.registrycenter.context.ZookeeperWindowsContext;
import org.apache.dubbo.test.check.registrycenter.initializer.ConfigZookeeperInitializer;
import org.apache.dubbo.test.check.registrycenter.initializer.DownloadZookeeperInitializer;
import org.apache.dubbo.test.check.registrycenter.initializer.UnpackZookeeperInitializer;
import org.apache.dubbo.test.check.registrycenter.initializer.ZookeeperInitializer;
import org.apache.dubbo.test.check.registrycenter.processor.StartZookeeperUnixProcessor;
import org.apache.dubbo.test.check.registrycenter.processor.StartZookeeperWindowsProcessor;
import org.apache.dubbo.test.check.registrycenter.processor.ResetZookeeperProcessor;
import org.apache.dubbo.test.check.registrycenter.processor.StopZookeeperUnixProcessor;
import org.apache.dubbo.test.check.registrycenter.processor.StopZookeeperWindowsProcessor;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Build the registry center with embedded zookeeper, which is run by a new process.
 */
class ZookeeperRegistryCenter implements RegistryCenter {

    public ZookeeperRegistryCenter() {
        this.initializers = new ArrayList<>();
        this.initializers.add(new DownloadZookeeperInitializer());
        this.initializers.add(new UnpackZookeeperInitializer());
        this.initializers.add(new ConfigZookeeperInitializer());
        // start processor
        this.put(OS.Unix, Command.Start, new StartZookeeperUnixProcessor());
        this.put(OS.Windows, Command.Start, new StartZookeeperWindowsProcessor());

        // reset processor
        Processor resetProcessor = new ResetZookeeperProcessor();
        this.put(OS.Unix, Command.Reset, resetProcessor);
        this.put(OS.Windows, Command.Reset, resetProcessor);

        // stop processor
        this.put(OS.Unix, Command.Stop, new StopZookeeperUnixProcessor());
        this.put(OS.Windows, Command.Stop, new StopZookeeperWindowsProcessor());

        // initialize the global context
        if (OS.Unix.equals(os)) {
            this.context = new ZookeeperContext();
        } else {
            this.context = new ZookeeperWindowsContext();
        }
    }

    /**
     * The OS type.
     */
    private static OS os = getOS();

    /**
     * All of {@link ZookeeperInitializer} instances.
     */
    private List<Initializer> initializers;

    /**
     * The global context of zookeeper.
     */
    private ZookeeperContext context;

    /**
     * To store all processor instances.
     */
    private Map<OS, Map<Command, Processor>> processors = new HashMap<>();

    /**
     * The {@link #INITIALIZED} for flagging the {@link #startup()} method is called or not.
     */
    private final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * Returns the Operating System.
     */
    private static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        OS os = OS.Unix;
        if (osName.contains("windows")) {
            os = OS.Windows;
        }
        return os;
    }

    /**
     * Store all initialized processor instances.
     *
     * @param os        the {@link OS} type.
     * @param command   the {@link Command} to execute.
     * @param processor the {@link Processor} to run.
     */
    private void put(OS os, Command command, Processor processor) {
        Map<Command, Processor> commandProcessorMap = this.processors.get(os);
        if (commandProcessorMap == null) {
            commandProcessorMap = new HashMap<>();
            this.processors.put(os, commandProcessorMap);
        }
        commandProcessorMap.put(command, processor);
    }

    /**
     * Gets the {@link Processor} with the given {@link OS} type and {@link Command}.
     *
     * @param os      the {@link OS} type.
     * @param command the {@link Command} to execute.
     * @return the {@link Processor} to run.
     */
    private Processor get(OS os, Command command) {
        Map<Command, Processor> commandProcessorMap = this.processors.get(os);
        Objects.requireNonNull(commandProcessorMap, "The command with the OS cannot be null");
        Processor processor = commandProcessorMap.get(command);
        Objects.requireNonNull(processor, "The processor cannot be null");
        return processor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startup() throws DubboTestException {
        if (!this.INITIALIZED.get()) {
            if (!this.INITIALIZED.compareAndSet(false, true)) {
                return;
            }
            for (Initializer initializer : this.initializers) {
                initializer.initialize(this.context);
            }
        }
        this.get(os, Command.Start).process(this.context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() throws DubboTestException {
        this.get(os, Command.Reset).process(this.context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() throws DubboTestException {
        this.get(os, Command.Stop).process(this.context);
    }

    /**
     * The type of OS.
     */
    enum OS {
        Windows,
        Unix
    }

    /**
     * The commands to support the zookeeper.
     */
    enum Command {
        Start,
        Reset,
        Stop
    }
}
