/**
 * Controller for the dashboard route.
 * Render proper template.
 */
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module)
        .controller('DashboardController', DashboardController);

    DashboardController.$inject = ['dashboard', 'templateData', '$location', "$scope", "$q"];
    function DashboardController(dashboard, templateData, $location, $scope, $q) {
        var ctrl = this;
        $scope.widgets = {};

        ctrl.dashboard = dashboard;
        ctrl.onChange = onChange;

        templateData.getTemplate({name: "Eric's Template"}).then(function(data) { // TO-DO: pass dashboard.template as name param
            processResponse(data);
        });

        function onChange(event, items) {
            console.log(items);
        };

        function processResponse(data) {
            ctrl.template = data;

            console.log(ctrl.template);

            console.log(ctrl.template); // templateData.getTemplate(dashboard.getTemplate())
            var widgetNames = Object.keys(ctrl.template.widgets);
            widgetNames.forEach(function(widgetName) {
                $scope.widgets[widgetName] = ctrl.template.widgets[widgetName];
            });
        }

        // if dashboard isn't available through resolve it may have been deleted
            // so redirect to the home screen
            if(!dashboard) {
                $location.path('/');
            }

            // set the template and make sure it has access to the dashboard objec
            // dashboard is guaranteed by the resolve setting in the route

            // public variables
    }
})();
