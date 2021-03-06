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
package org.jboss.pnc.bacon.pnc;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.pnc.bacon.common.ObjectHelper;
import org.jboss.pnc.bacon.common.cli.AbstractCommand;
import org.jboss.pnc.bacon.common.cli.AbstractGetSpecificCommand;
import org.jboss.pnc.bacon.common.cli.AbstractListCommand;
import org.jboss.pnc.bacon.pnc.common.ClientCreator;
import org.jboss.pnc.client.ClientException;
import org.jboss.pnc.client.ProductClient;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Product;
import org.jboss.pnc.dto.ProductVersion;

import java.util.Optional;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

@GroupCommandDefinition(
        name = "product",
        description = "Product",
        groupCommands = {
                ProductCli.Create.class,
                ProductCli.Get.class,
                ProductCli.List.class,
                ProductCli.ListVersions.class,
                ProductCli.Update.class })
public class ProductCli extends AbstractCommand {

    private static final ClientCreator<ProductClient> CREATOR = new ClientCreator<>(ProductClient::new);

    @CommandDefinition(name = "create", description = "Create a product")
    public class Create extends AbstractCommand {

        @Argument(required = true, description = "Name of product")
        private String name;

        @Option(required = true, name = "abbreviation", description = "Abbreviation of product")
        private String abbreviation;

        @Option(name = "description", description = "Description of product", defaultValue = "")
        private String description;

        @Option(
                shortName = 'o',
                overrideRequired = false,
                hasValue = false,
                description = "use json for output (default to yaml)")
        private boolean jsonOutput = false;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation)
                throws CommandException, InterruptedException {

            return super.executeHelper(commandInvocation, () -> {

                Product product = Product.builder()
                        .name(name)
                        .abbreviation(abbreviation)
                        .description(description)
                        .build();

                try (ProductClient client = CREATOR.newClientAuthenticated()) {
                    ObjectHelper.print(jsonOutput, client.createNew(product));
                    return 0;
                }
            });
        }

        @Override
        public String exampleText() {
            return "$ bacon pnc product create --abbreviation testing Testing";
        }
    }

    @CommandDefinition(name = "get", description = "Get a product by its id")
    public class Get extends AbstractGetSpecificCommand<Product> {

        @Override
        public Product getSpecific(String id) throws ClientException {
            try (ProductClient client = CREATOR.newClient()) {
                return client.getSpecific(id);
            }
        }
    }

    @CommandDefinition(name = "list", description = "List products")
    public class List extends AbstractListCommand<Product> {

        @Override
        public RemoteCollection<Product> getAll(String sort, String query) throws RemoteResourceException {

            try (ProductClient client = CREATOR.newClient()) {
                return client.getAll(Optional.ofNullable(sort), Optional.ofNullable(query));
            }
        }
    }

    @CommandDefinition(name = "list-versions", description = "List versions of product")
    public class ListVersions extends AbstractListCommand<ProductVersion> {

        @Argument(required = true, description = "Product Id")
        private String id;

        @Override
        public RemoteCollection<ProductVersion> getAll(String sort, String query) throws RemoteResourceException {

            try (ProductClient client = CREATOR.newClient()) {
                return client.getProductVersions(id, Optional.ofNullable(sort), Optional.ofNullable(query));
            }
        }
    }

    @CommandDefinition(name = "update", description = "Update a product")
    public class Update extends AbstractCommand {

        @Argument(required = true, description = "Product Id")
        private String id;

        @Option(name = "name", description = "Name of product")
        private String name;

        @Option(name = "abbreviation", description = "Abbreviation of product")
        private String abbreviation;

        @Option(name = "description", description = "Description of product", defaultValue = "")
        private String description;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation)
                throws CommandException, InterruptedException {

            return super.executeHelper(commandInvocation, () -> {

                try (ProductClient client = CREATOR.newClient()) {
                    Product product = client.getSpecific(id);
                    Product.Builder updated = product.toBuilder();

                    if (isNotEmpty(name)) {
                        updated.name(name);
                    }
                    if (isNotEmpty(abbreviation)) {
                        updated.abbreviation(abbreviation);
                    }
                    if (isNotEmpty(description)) {
                        updated.description(description);
                    }

                    try (ProductClient authenticatedClient = CREATOR.newClientAuthenticated()) {
                        authenticatedClient.update(id, updated.build());
                        return 0;
                    }
                }
            });
        }

        @Override
        public String exampleText() {
            return "$ bacon pnc product update --abbreviation testingme2 42";
        }
    }
}
