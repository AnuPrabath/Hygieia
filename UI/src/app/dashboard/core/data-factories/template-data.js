/**
 * Gets template data
 */

(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module + '.core')
        .factory('templateData', templateData);

    function templateData($http) {
        // var templateRoute = 'api/template/application';
        var testTemplateRoute = 'test-data/templates.json';

        return {
            getTemplates: getTemplates,
            getTemplate: getTemplate
        };

        function getTemplates(params) {
            return $http.get(testTemplateRoute, {params: params})
                .then(function (response) {
                    return response.data;
                });
        }


        function getTemplate(params) {
            return $http.get(testTemplateRoute, {params: params})
                .then(function (response) {
                    for (var i = 0; i < response.data.result.length; i++)
                        if (response.data.result[i].name === params.name) return response.data.result[i];
                });
        }
    }
})();
