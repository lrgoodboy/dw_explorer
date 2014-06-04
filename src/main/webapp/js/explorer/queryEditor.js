define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/request',
    'dojo/json'
], function(declare, lang, config, request, json) {

    var QueryEditor = declare(null, {

        constructor: function() {
        },

        submitTask: function(queries) {

            queries = lang.trim(queries);
            if (!queries) {
                alert('Queries cannot be empty.');
                return false;
            }

            request.post(config.contextPath + '/query-editor/api/task', {
                data: json.stringify({queries: queries}),
                headers: {'content-type': 'application/json'},
                handleAs: 'json'
            }).then(function(result) {

                if (result.status != 'ok') {
                    alert(result.msg);
                    return false;
                }

                console.log(result.id);
            });
        }

    });

    return new QueryEditor();

});
