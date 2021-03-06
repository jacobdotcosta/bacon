/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bacon.pig;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.pnc.bacon.common.ObjectHelper;
import org.jboss.pnc.bacon.common.cli.AbstractCommand;
import org.jboss.pnc.bacon.common.exception.FatalException;
import org.jboss.pnc.bacon.config.Config;
import org.jboss.pnc.bacon.config.PigConfig;
import org.jboss.pnc.bacon.config.Validate;
import org.jboss.pnc.bacon.pig.impl.PigContext;
import org.jboss.pnc.bacon.pig.impl.config.GroupBuildInfo;
import org.jboss.pnc.bacon.pig.impl.out.PigReleaseOutput;
import org.jboss.pnc.bacon.pig.impl.out.PigRunOutput;
import org.jboss.pnc.bacon.pig.impl.pnc.ImportResult;
import org.jboss.pnc.bacon.pig.impl.repo.RepositoryData;
import org.jboss.pnc.bacon.pnc.common.ParameterChecker;
import org.jboss.pnc.enums.RebuildMode;

import java.util.Optional;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com <br>
 *         Date: 12/13/18
 */
@GroupCommandDefinition(
        name = "pig",
        description = "PiG tool",
        groupCommands = {
                Pig.Configure.class,
                Pig.Build.class,
                Pig.Run.class,
                Pig.GenerateRepository.class,
                Pig.GenerateLicenses.class,
                Pig.GenerateJavadocs.class,
                Pig.GenerateSources.class,
                Pig.GenerateSharedContentAnalysis.class,
                Pig.GenerateDocuments.class,
                Pig.Release.class,
                Pig.TriggerAddOns.class })
public class Pig extends AbstractCommand {

    public static final String REBUILD_MODE_DESC = "The build mode EXPLICIT_DEPENDENCY_CHECK, IMPLICIT_DEPENDENCY_CHECK, FORCE. Defaults to EXPLICIT";
    public static final String REBUILD_MODE_DEFAULT = "EXPLICIT_DEPENDENCY_CHECK";
    public static final String REBUILD_MODE = "mode";
    public static final String TEMP_BUILD_TIME_STAMP = "tempBuildTimeStamp";
    public static final String TEMP_BUILD_TIME_STAMP_DEFAULT = "false";
    public static final String TEMP_BUILD_TIME_STAMP_DESC = "If specified, artifacts from temporary builds will have timestamp in versions";
    public static final String TEMP_BUILD_DESC = "If specified, PNC will perform temporary builds";
    public static final String TEMP_BUILD_DEFAULT = "false";
    public static final String TEMP_BUILD = "tempBuild";
    public static final char TEMP_BUILD_SHORT = 't';
    public static final String REMOVE_M2_DUPLICATES_DESC = "If enabled, only the newest versions of each of the dependencies (groupId:artifactId) "
            + "are kept in the generated repository zip";
    public static final String REMOVE_M2_DUPLICATES = "removeGeneratedM2Dups";
    public static final String REMOVE_M2_DUPLICATES_DEFAULT = "false";

    public static final String SKIP_BRANCH_CHECK = "skipBranchCheck";
    public static final String SKIP_BRANCH_CHECK_DEFAULT = "false";
    public static final String SKIP_BRANCH_CHECK_DESC = "If set to true, pig won't try to determine if the branch that is used to build from is modified. "
            + "Branch modification check takes a lot of time, if you use tag, this switch can speed up the build.";

    public abstract class PigCommand<T> extends AbstractCommand {
        @Argument(required = true, description = "Directory containing the Pig configuration file")
        String configDir;

        @Option(
                shortName = TEMP_BUILD_SHORT,
                name = TEMP_BUILD,
                hasValue = false,
                defaultValue = TEMP_BUILD_DEFAULT,
                description = TEMP_BUILD_DESC)
        boolean tempBuild;

        @Option(shortName = 'o', hasValue = false, description = "use json for output (default to yaml)")
        private boolean jsonOutput = false;

        @Option(
                name = "releaseStorageUrl",
                description = "Location of the release storage, typically on rcm-guest staging. Required for auto-incremented milestones, e.g. DR*")
        private String releaseStorageUrl;

        @Option(
                name = "clean",
                hasValue = false,
                defaultValue = "false",
                description = "If enabled, the pig execution will not attempt to continue the previous execution")
        private boolean clean;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation)
                throws CommandException, InterruptedException {

            return super.executeHelper(commandInvocation, () -> {

                if (configDir == null) {
                    throw new FatalException("You need to specify the configuration directory!");
                }
                // validate the PiG config
                PigConfig pig = Config.instance().getActiveProfile().getPig();
                if (pig == null) {
                    throw new Validate.ConfigMissingException("Pig configuration missing");
                }
                pig.validate();

                Optional<String> releaseStorageUrl = Optional.ofNullable(this.releaseStorageUrl);
                PigContext.init(clean || isStartingPoint(), configDir, releaseStorageUrl);
                ObjectHelper.print(jsonOutput, doExecute());
                return 0;
            });
        }

        boolean isStartingPoint() {
            return false;
        }

        abstract T doExecute();
    }

    /* System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "10"); */
    @CommandDefinition(name = "run", description = "Run all the steps")
    public class Run extends PigCommand<PigRunOutput> {

        // TODO: it is doable to do this step with build group id only, add this functionality
        // @Option(shortName = 'b',
        // description = "id of the group to build. Exactly one of {config, build-group} has to be provided")
        // private Integer buildGroupId;

        @Option(
                name = TEMP_BUILD_TIME_STAMP,
                hasValue = false,
                defaultValue = TEMP_BUILD_TIME_STAMP_DEFAULT,
                description = TEMP_BUILD_TIME_STAMP_DESC)
        private boolean tempBuildTS;

        @Option(name = REBUILD_MODE, defaultValue = REBUILD_MODE_DEFAULT, description = REBUILD_MODE_DESC)
        private String rebuildMode;

        @Option(
                name = "skipRepo",
                hasValue = false,
                defaultValue = "false",
                description = "Skip maven repository generation")
        private boolean skipRepo;

        @Option(
                name = "skipPncUpdate",
                hasValue = false,
                defaultValue = "false",
                description = "Skip updating PNC entities. Use only if you have all entities created properly.")
        private boolean skipPncUpdate;

        @Option(
                name = "skipBuilds",
                hasValue = false,
                defaultValue = "false",
                description = "Skip PNC builds. Use when all your builds went fine, something failed later "
                        + "and you want to retry generating deliverables without rebuilding.")
        private boolean skipBuilds;

        @Option(
                name = "skipSources",
                hasValue = false,
                defaultValue = "false",
                description = "Skip sources generation.")
        private boolean skipSources;

        @Option(
                name = "skipJavadoc",
                hasValue = false,
                defaultValue = "false",
                description = "Skip Javadoc generation.")
        private boolean skipJavadoc;

        @Option(
                name = "skipLicenses",
                hasValue = false,
                defaultValue = "false",
                description = "Skip Licenses generation.")
        private boolean skipLicenses;

        @Option(
                name = "skipSharedContent",
                hasValue = false,
                defaultValue = "false",
                description = "Skip generating shared content request input.")
        private boolean skipSharedContent;

        @Option(
                name = REMOVE_M2_DUPLICATES,
                hasValue = false,
                defaultValue = REMOVE_M2_DUPLICATES_DEFAULT,
                description = REMOVE_M2_DUPLICATES_DESC)
        private boolean removeGeneratedM2Dups;

        @Option(
                name = SKIP_BRANCH_CHECK,
                hasValue = false,
                defaultValue = SKIP_BRANCH_CHECK_DEFAULT,
                description = SKIP_BRANCH_CHECK_DESC)
        private boolean skipBranchCheck;

        @Option(
                shortName = 'r',
                name = "repoZip",
                description = "Repository zip. "
                        + "Might be used if you have already downloaded repository zip to speed up the process.")
        private String repoZipPath;

        @Override
        public PigRunOutput doExecute() {

            ParameterChecker.checkRebuildModeOption(rebuildMode);

            GroupBuildInfo groupBuildInfo = PigFacade.run(
                    skipRepo,
                    skipPncUpdate,
                    skipBuilds,
                    skipSources,
                    skipJavadoc,
                    skipLicenses,
                    skipSharedContent,
                    removeGeneratedM2Dups,
                    repoZipPath,
                    tempBuild,
                    tempBuildTS,
                    RebuildMode.valueOf(rebuildMode),
                    skipBranchCheck);

            PigContext context = PigContext.get();
            return new PigRunOutput(
                    context.getFullVersion(),
                    groupBuildInfo,
                    context.getReleaseDirName(),
                    context.getReleasePath());
        }

        @Override
        boolean isStartingPoint() {
            return true;
        }
    }

    @CommandDefinition(name = "configure", description = "Configure PNC entities")
    public class Configure extends PigCommand<ImportResult> {

        @Option(
                name = SKIP_BRANCH_CHECK,
                hasValue = false,
                defaultValue = SKIP_BRANCH_CHECK_DEFAULT,
                description = SKIP_BRANCH_CHECK_DESC)
        private boolean skipBranchCheck;

        @Override
        public ImportResult doExecute() {
            ImportResult importResult = PigFacade.configure(skipBranchCheck, tempBuild);
            PigContext.get().setPncImportResult(importResult);
            PigContext.get().storeContext();
            return importResult;
        }

        @Override
        boolean isStartingPoint() {
            return true;
        }
    }

    @CommandDefinition(name = "build", description = "Build")
    public class Build extends PigCommand<PigRunOutput> {

        // TODO: it is doable to do this step with build group id only, add this functionality
        // @Option(shortName = 'b',
        // description = "id of the group to build. Exactly one of {config, build-group} has to be provided")
        // private Integer buildGroupId;

        @Option(
                name = TEMP_BUILD_TIME_STAMP,
                hasValue = false,
                defaultValue = TEMP_BUILD_TIME_STAMP_DEFAULT,
                description = TEMP_BUILD_TIME_STAMP_DESC)
        private boolean tempBuildTS;

        @Option(name = REBUILD_MODE, defaultValue = REBUILD_MODE_DEFAULT, description = REBUILD_MODE_DESC)
        private String rebuildMode;

        @Override
        public PigRunOutput doExecute() {

            ParameterChecker.checkRebuildModeOption(rebuildMode);
            GroupBuildInfo groupBuildInfo = PigFacade.build(tempBuild, tempBuildTS, RebuildMode.valueOf(rebuildMode));
            PigContext context = PigContext.get();
            context.setBuilds(groupBuildInfo.getBuilds());
            context.storeContext();
            return new PigRunOutput(
                    context.getFullVersion(),
                    groupBuildInfo,
                    context.getReleaseDirName(),
                    context.getReleasePath());
        }
    }

    @CommandDefinition(name = "repo", description = "GenerateRepository")
    public class GenerateRepository extends PigCommand<RepositoryData> {
        @Option(
                name = REMOVE_M2_DUPLICATES,
                hasValue = false,
                defaultValue = REMOVE_M2_DUPLICATES_DEFAULT,
                description = REMOVE_M2_DUPLICATES_DESC)
        private boolean removeGeneratedM2Dups;

        @Override
        public RepositoryData doExecute() {
            RepositoryData result = PigFacade.generateRepo(removeGeneratedM2Dups);
            PigContext.get().setRepositoryData(result);
            PigContext.get().storeContext();
            return result;
        }
    }

    @CommandDefinition(name = "licenses", description = "GenerateLicenses")
    public class GenerateLicenses extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.generateLicenses();
            return "Licenses generated successfully"; // TODO: better output
        }
    }

    @CommandDefinition(name = "javadocs", description = "GenerateJavadocs")
    public class GenerateJavadocs extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.generateJavadoc();
            return "Javadocs generated successfully"; // TODO: better output
        }
    }

    @CommandDefinition(name = "sources", description = "GenerateSources")
    public class GenerateSources extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.generateSources();
            return "Sources gathered successfully"; // TODO: better output
        }
    }

    @CommandDefinition(name = "shared-content", description = "GenerateSharedContentAnalysis")
    public class GenerateSharedContentAnalysis extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.prepareSharedContentAnalysis();
            return "Shared content analysis document prepared successfully"; // TODO: better output
        }
    }

    @CommandDefinition(name = "docs", description = "GenerateDocuments")
    public class GenerateDocuments extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.generateDocuments();
            return "Documents generated successfully"; // TODO: better output
        }
    }

    @CommandDefinition(name = "addons", description = "Addons")
    public class TriggerAddOns extends PigCommand<String> {

        @Override
        public String doExecute() {
            PigFacade.triggerAddOns();
            return "Add-ons executed successfully";
        }
    }

    @CommandDefinition(
            name = "release",
            description = "Push builds to brew, generate the NVR list, "
                    + "close the PNC milestone, generate the upload to candidates script")
    public class Release extends PigCommand<PigReleaseOutput> {

        @Override
        public PigReleaseOutput doExecute() {
            return PigFacade.release();
        }
    }

}
