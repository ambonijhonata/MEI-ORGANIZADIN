# Ruido (PMD)

Inventario de supressoes `@SuppressWarnings("PMD....")` existentes na API.

Objetivo: remover estas supressoes corrigindo as causas-raiz (sem silenciar o PMD).

Como atualizar/regenerar esta lista:
- Rodar uma busca por `@SuppressWarnings` contendo `PMD.` em `API/src/main/java/**`.

## Itens

- `API/src/main/java/com/api/auth/RefreshSessionToken.java:14` - PMD.AtLeastOneConstructor, PMD.DataClass, PMD.LongVariable, PMD.ShortVariable
- `API/src/main/java/com/api/auth/RefreshTokenService.java:20` - PMD.AvoidDeeplyNestedIfStmts, PMD.CognitiveComplexity, PMD.CommentDefaultAccessModifier, PMD.ControlStatementBraces, PMD.FieldNamingConventions, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn, PMD.SimplifyBooleanReturns, PMD.TooManyMethods
- `API/src/main/java/com/api/auth/SecurityConfig.java:19` - PMD.LambdaCanBeMethodReference, PMD.LongVariable, PMD.SignatureDeclareThrowsException
- `API/src/main/java/com/api/auth/SessionTokenProperties.java:6` - PMD.AtLeastOneConstructor, PMD.DataClass, PMD.LongVariable
- `API/src/main/java/com/api/calendar/CalendarController.java:34` - PMD.AvoidCatchingGenericException, PMD.CommentDefaultAccessModifier, PMD.CouplingBetweenObjects, PMD.FieldNamingConventions, PMD.GuardLogStatement, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn, PMD.PreserveStackTrace, PMD.ShortVariable
- `API/src/main/java/com/api/calendar/CalendarEvent.java:18` - PMD.CognitiveComplexity, PMD.CommentDefaultAccessModifier, PMD.CyclomaticComplexity, PMD.GodClass, PMD.ImmutableField, PMD.LawOfDemeter, PMD.LongVariable, PMD.NullAssignment, PMD.OnlyOneReturn, PMD.RedundantFieldInitializer, PMD.ShortVariable, PMD.TooManyFields, PMD.TooManyMethods, PMD.UseExplicitTypes
- `API/src/main/java/com/api/calendar/CalendarEventPayment.java:8` - PMD.CommentDefaultAccessModifier, PMD.DataClass, PMD.ShortVariable
- `API/src/main/java/com/api/calendar/CalendarEventRepository.java:14` - PMD.AvoidDuplicateLiterals, PMD.ShortVariable, PMD.TooManyMethods
- `API/src/main/java/com/api/calendar/CalendarEventReprocessor.java:13` - PMD.AvoidLiteralsInIfCondition, PMD.CyclomaticComplexity, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn
- `API/src/main/java/com/api/calendar/CalendarEventServiceLink.java:8` - PMD.CommentDefaultAccessModifier, PMD.DataClass, PMD.LawOfDemeter, PMD.LongVariable, PMD.ShortVariable
- `API/src/main/java/com/api/calendar/CalendarEventServiceLinkBulkRepository.java:4` - PMD.ImplicitFunctionalInterface
- `API/src/main/java/com/api/calendar/CalendarSyncService.java:38` - PMD.AvoidCatchingGenericException, PMD.AvoidInstantiatingObjectsInLoops, PMD.AvoidThrowingRawExceptionTypes, PMD.CognitiveComplexity, PMD.CommentDefaultAccessModifier, PMD.CompareObjectsWithEquals, PMD.ConfusingTernary, PMD.ControlStatementBraces, PMD.CouplingBetweenObjects, PMD.CyclomaticComplexity, PMD.ExcessiveImports, PMD.ExcessiveParameterList, PMD.FieldNamingConventions, PMD.GodClass, PMD.GuardLogStatement, PMD.LawOfDemeter, PMD.LongVariable, PMD.LooseCoupling, PMD.MethodArgumentCouldBeFinal, PMD.NPathComplexity, PMD.OnlyOneReturn, PMD.PreserveStackTrace, PMD.SimplifyBooleanReturns, PMD.TooManyMethods, PMD.UnusedAssignment
- `API/src/main/java/com/api/calendar/EventTitleParser.java:13` - PMD.AtLeastOneConstructor, PMD.AvoidLiteralsInIfCondition, PMD.LongVariable, PMD.NullAssignment, PMD.OnlyOneReturn, PMD.ShortVariable
- `API/src/main/java/com/api/calendar/ManualAppointmentService.java:25` - PMD.CyclomaticComplexity, PMD.LongVariable
- `API/src/main/java/com/api/calendar/SyncState.java:22` - PMD.TooManyMethods
- `API/src/main/java/com/api/calendar/UserScopedExecutionLock.java:10` - PMD.AtLeastOneConstructor, PMD.OnlyOneReturn
- `API/src/main/java/com/api/client/Client.java:8` - PMD.CommentDefaultAccessModifier, PMD.DataClass, PMD.ShortVariable, PMD.UseExplicitTypes
- `API/src/main/java/com/api/client/ClientController.java:24` - PMD.AvoidDuplicateLiterals, PMD.CommentDefaultAccessModifier, PMD.LawOfDemeter, PMD.LinguisticNaming, PMD.LongVariable, PMD.ShortVariable, PMD.UseExplicitTypes
- `API/src/main/java/com/api/client/ClientRepository.java:12` - PMD.ShortVariable
- `API/src/main/java/com/api/client/ClientService.java:19` - PMD.LinguisticNaming, PMD.LongVariable, PMD.OnlyOneReturn, PMD.UseExplicitTypes
- `API/src/main/java/com/api/common/BusinessException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/common/GlobalExceptionHandler.java:17` - PMD.AtLeastOneConstructor, PMD.AvoidDuplicateLiterals, PMD.FieldNamingConventions, PMD.GuardLogStatement, PMD.OnlyOneReturn, PMD.ShortVariable, PMD.TooManyMethods, PMD.UseExplicitTypes
- `API/src/main/java/com/api/common/GoogleApiAccessDeniedException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/common/IntegrationRevokedException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/common/InvalidPeriodException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/common/InvalidRequestParameterException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/common/OpenApiConfig.java:10` - PMD.AtLeastOneConstructor
- `API/src/main/java/com/api/common/PageRequestSanitizer.java:9` - PMD.AvoidLiteralsInIfCondition, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn, PMD.PrematureDeclaration, PMD.UseExplicitTypes, PMD.UseObjectForClearerAPI
- `API/src/main/java/com/api/common/ResourceNotFoundException.java:2` - PMD.MissingSerialVersionUID
- `API/src/main/java/com/api/google/GoogleCalendarClient.java:25` - PMD.AvoidInstantiatingObjectsInLoops, PMD.AvoidLiteralsInIfCondition, PMD.CognitiveComplexity, PMD.CommentDefaultAccessModifier, PMD.CyclomaticComplexity, PMD.LawOfDemeter, PMD.LongVariable, PMD.LooseCoupling, PMD.MissingSerialVersionUID, PMD.OnlyOneReturn, PMD.PreserveStackTrace, PMD.ShortVariable, PMD.UnusedAssignment
- `API/src/main/java/com/api/report/CashFlowReportService.java:13` - PMD.AvoidInstantiatingObjectsInLoops, PMD.AvoidLiteralsInIfCondition, PMD.ControlStatementBraces, PMD.CouplingBetweenObjects, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn, PMD.UseExplicitTypes
- `API/src/main/java/com/api/report/PaymentMethodRevenueReportService.java:25` - PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn
- `API/src/main/java/com/api/report/ReportController.java:18` - PMD.LongVariable
- `API/src/main/java/com/api/report/ReportPaidAmountService.java:16` - PMD.LongVariable, PMD.OnlyOneReturn
- `API/src/main/java/com/api/report/RevenueReportService.java:16` - PMD.ControlStatementBraces, PMD.LawOfDemeter, PMD.LongVariable, PMD.OnlyOneReturn
- `API/src/main/java/com/api/servicecatalog/Service.java:12` - PMD.CommentDefaultAccessModifier, PMD.DataClass, PMD.LongVariable, PMD.ShortVariable, PMD.UseExplicitTypes
- `API/src/main/java/com/api/servicecatalog/ServiceCatalogController.java:24` - PMD.CommentDefaultAccessModifier, PMD.LawOfDemeter, PMD.LongVariable, PMD.ShortVariable
- `API/src/main/java/com/api/servicecatalog/ServiceCatalogService.java:24` - PMD.LinguisticNaming, PMD.LongVariable, PMD.OnlyOneReturn
- `API/src/main/java/com/api/servicecatalog/ServiceDescriptionNormalizer.java:7` - PMD.AtLeastOneConstructor, PMD.LongVariable, PMD.OnlyOneReturn, PMD.ShortVariable, PMD.UseLocaleWithCaseConversions
- `API/src/main/java/com/api/servicecatalog/ServiceRepository.java:10` - PMD.LongVariable, PMD.ShortVariable
- `API/src/main/java/com/api/user/User.java:6` - PMD.CommentDefaultAccessModifier, PMD.DataClass, PMD.ShortClassName, PMD.ShortVariable, PMD.UseExplicitTypes
