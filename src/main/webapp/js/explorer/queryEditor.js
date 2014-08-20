define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/ready',
    'dojo/query',
    'dojo/html',
    'dojo/dom-style',
    'dojo/dom-attr',
    'dojo/on',
    'dojo/date/locale',
    'dojo/request',
    'dojo/json',
    'dojo/store/Memory',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dojo/promise/all',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/layout/LayoutContainer',
    'dijit/Tree',
    'dijit/tree/ObjectStoreModel',
    'dijit/Menu',
    'dijit/MenuItem',
    'dijit/form/Select',
    'dijit/form/TextBox',
    'dijit/form/Button',
    'dijit/form/CheckBox',
    'dijit/form/NumberSpinner',
    'dijit/Toolbar',
    'dijit/Fieldset',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dgrid/util/misc',
    'put-selector/put',
    'cm/lib/codemirror',
    'cm/mode/sql/sql',
    'explorer/queryEditor/taskStatus'
], function(declare, lang, config, array, ready, query, html, domStyle, domAttr, on, date, request, json, Memory, JsonRest, Observable, all,
            registry, ContentPane, LayoutContainer, Tree, ObjectStoreModel, Menu, MenuItem, Select, TextBox, Button,
            CheckBox, NumberSpinner, Toolbar, Fieldset,
            Grid, OnDemandGrid, Selection, ColumnResizer, dgridUtil, put, CodeMirror, cmdModeSql, taskStatus) {

    var QueryEditor = declare(null, {

        constructor: function() {
            var self = this;
            ready(function() {
                self.initDocument();
                self.initMetadata();
                self.initOption();
            });
        },

        initDocument: function() {
            var self = this;

            // document tree
            var rest = JsonRest({
                target: config.contextPath + '/query-editor/api/doc/',
            });

            rest.query().then(function(docs) {

                var store = Observable(Memory({
                    data: docs,
                    getChildren: function(object) {
                        return this.query({parent: object.id}, {
                            sort: [
                                {attribute: 'isFolder', descending: true},
                                {attribute: 'name', descending: false}
                            ]
                        });
                    }
                }));

                var model = new ObjectStoreModel({
                    store: store,
                    query: {id: 0},
                    mayHaveChildren: function(item) {
                        return item.isFolder;
                    },
                });

                self.treeDoc = new Tree({
                    model: model,
                    onDblClick: function(item) {

                        if (item.isFolder) {
                            return;
                        }

                        var central = registry.byId('central');
                        var paneId = 'editorPane_' + item.id;
                        var pane = registry.byId(paneId);

                        if (typeof pane != 'undefined') {
                            central.selectChild(pane);
                            return;
                        }

                        rest.get(item.id).then(function(object) {

                            // http://dojotoolkit.org/reference-guide/1.10/dijit/layout.html
                            // layout
                            var pane = new ContentPane({
                                id: paneId,
                                title: object.name,
                                closable: true
                            });

                            var layout = new LayoutContainer({
                                style: 'width: 100%; height: 100%; border: 1px solid silver;'
                            });

                            var toolbarPane = new ContentPane({
                                region: 'top',
                                style: 'padding: 0;'
                            });

                            var toolbar = new Toolbar();

                            var btnSave = new Button({
                                label: '保存'
                            });
                            toolbar.addChild(btnSave);

                            var btnRunSelected = new Button({
                                label: '运行所选'
                            });
                            toolbar.addChild(btnRunSelected);

                            var btnRunAll = new Button({
                                label: '运行全部'
                            });
                            toolbar.addChild(btnRunAll);

                            toolbarPane.addChild(toolbar);
                            layout.addChild(toolbarPane);

                            var editorPane = new ContentPane({
                                region: 'center',
                                style: 'height: 100%; padding: 0;'
                            });

                            layout.addChild(editorPane);
                            pane.addChild(layout);

                            central.addChild(pane);
                            central.selectChild(pane);

                            // editor
                            var editor = CodeMirror(editorPane.domNode, {
                                value: object.content,
                                mode: 'text/x-hive'
                            });

                            editor.on('change', function() {
                                self.autoSave[item.id] = true;
                            });

                            pane.codeMirror = editor;

                            btnSave.on('click', function() {

                                rest.put({
                                    id: item.id,
                                    content: editor.getValue()
                                }).then(function() {
                                    self.showToaster('文档 ' + object.name + ' 保存成功');
                                });

                            });

                            btnRunSelected.on('click', function() {
                                taskStatus.submitTask(self.getOptions() + editor.getSelection());
                            });

                            btnRunAll.on('click', function() {
                                taskStatus.submitTask(self.getOptions() + editor.getValue());
                            });

                        });
                    }
                }, 'treeDoc');

                // context menu
                var menu = new Menu({
                    targetNodeIds: [self.treeDoc.domNode],
                    selector: '.dijitTreeNode'
                });

                menu.addChild(new MenuItem({
                    label: '新建文件',
                    onClick: function() {

                        var name = prompt('请输入文件名');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.add({
                            parent: item.isFolder ? item.id : item.parent,
                            filename: name,
                            isFolder: false
                        }).then(function(doc) {
                            store.notify(doc);
                        });
                    }
                }));

                menu.addChild(new MenuItem({
                    label: '新建文件夹',
                    onClick: function() {

                        var name = prompt('请输入文件夹名称');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.add({
                            parent: item.isFolder ? item.id : item.parent,
                            filename: name,
                            isFolder: true
                        }).then(function(doc) {
                            store.notify(doc);
                        });

                    }
                }));

                menu.addChild(new MenuItem({
                    label: '重命名',
                    onClick: function() {
                        var name = prompt('请输入新的名称');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.put({
                            id: item.id,
                            filename: name
                        }).then(function(doc) {
                            store.notify(doc, doc.id);
                        });

                    }
                }));

                menu.addChild(new MenuItem({
                    label: '删除',
                    onClick: function() {
                        if (!confirm('确定要删除吗？')) {
                            return;
                        }
                        var item = registry.byNode(this.getParent().currentTarget).item;
                        rest.remove(item.id);
                        store.notify(null, item.id);
                    }
                }));

                self.treeDoc.startup();
            });

            // auto save task
            setInterval(function() {

                var autoSave = self.autoSave;
                self.autoSave = {};

                var savings = [];

                for (id in autoSave) {

                    var pane = registry.byId('editorPane_' + id);
                    if (!pane || !pane.codeMirror) {
                        return;
                    }

                    savings.push({
                        promise: rest.put({
                            id: id,
                            content: pane.codeMirror.getValue()
                        }),
                        title: pane.get('title')
                    });
                };

                if (savings.length == 0) {
                    return;
                }

                var promises = array.map(savings, function(item) {
                    return item.promise;
                });

                all(promises).then(function() {

                    var titles = array.map(savings, function(item) {
                        return item.title;
                    });

                    self.showToaster('以下文档已自动保存：<br>' + titles.join('<br>'));
                });

            }, 30000);
        },

        autoSave: {},

        showToaster: function(content) {
            var toaster = registry.byId('toaster');
            toaster.setContent(content);
            toaster.show();
        },

        initMetadata: function() {
            var self = this;

            domStyle.set('loadingTables', 'display', 'none');

            var cache = {};

            var store = new JsonRest({
                target: config.contextPath + '/query-editor/api/metadata/'
            });

            store.query().then(function(databases) {

                var options = [{label: '请选择数据库', value: '', selected: true}];
                array.forEach(databases, function(database) {
                    options.push({label: database.id, value: database.name});
                });

                var select = new Select({
                    options: options,
                    autoWidth: true,
                    style: {width: '100%'}
                }, 'selDatabase');

                select.on('change', function() {

                    domStyle.set('divTables', 'display', 'none');

                    var database = this.get('value');
                    if (!database) {
                        return;
                    }

                    var table = this.get('value');

                    if (!(table in cache)) {
                        domStyle.set('loadingTables', 'display', '');
                        store.query({database: database}).then(function(tables) {
                            cache[database] = tables;
                            domStyle.set('loadingTables', 'display', 'none');
                            domStyle.set('divTables', 'display', '');
                            txtTable.set('value', '');
                            searchTable();
                        });
                    } else {
                        domStyle.set('divTables', 'display', '');
                        txtTable.set('value', '');
                        searchTable();
                    }

                });

                select.startup();
            });

            // search box
            var txtTable = new TextBox({
                placeHolder: '请输入表名关键字',
                trim: true,
                style: {width: '100%'}
            }, 'txtTable');

            var searchTable = dgridUtil.debounce(function() {

                var database = registry.byId('selDatabase').get('value');
                var table = registry.byId('txtTable').get('value');

                var MAX = 20;
                var result = [];

                if (!table) {
                    for (var i = 0; i < MAX && i < cache[database].length; ++i) {
                        result.push(cache[database][i]);
                    }
                } else {

                    array.every(cache[database], function(object) {
                        if (object.name.indexOf(table) !== -1) {
                            result.push(object);
                        }
                        return result.length < MAX;
                    });

                }

                var ulTables = query('#ulTables')[0];
                query('li', ulTables).forEach(function(li) {
                    put(li, '!');
                });

                if (result.length == 0) {
                    put(ulTables, 'li', '查无结果');
                } else {
                    array.forEach(result, function(object) {
                        var li = put(ulTables, 'li');
                        var anchor =  put(li, 'a[href="javascript:void(0);"]', object.name);
                        on(anchor, 'click', function() {
                            var segs = object.id.split(/\./);
                            self.descTable(segs[0], segs[1]);
                        });
                    });
                }

            }, null, 500);

            txtTable.on('input', function(evt) {
                searchTable();
            });

            txtTable.startup();
        },

        descTable: function(database, table) {
            var self = this;

            var pane = new ContentPane({
                title: '表信息[' + table + ']',
                closable: true
            });

            var bottomCol = registry.byId('bottomCol');
            bottomCol.addChild(pane);
            bottomCol.selectChild(pane);

            var loading = put(pane.domNode, 'div[style="width: 100%; margin: 10px;"]');
            put(loading, 'img[src="' + config.contextPath + '/webjars/dojo/1.9.3/dijit/themes/claro/images/loadingAnimation.gif"][align="top"]');
            put(loading, 'span', '加载中……');

            request(config.contextPath + '/query-editor/api/metadata/desc/', {
                query: {database: database, table: table},
                handleAs: 'json'
            }).then(function(result) {

                put(loading, '!');

                if (result.columns.length == 0) {
                    put(pane.domNode, 'div[style="color: red;"]', '加载失败');
                    return;
                }

                var infoGrid  = new Grid({
                    className: 'dgrid-autoheight',
                    columns: [
                        {label: '数据库', field: 'database', sortable: false},
                        {label: '表名', field: 'table', sortable: false},
                        {label: '分区字段', field: 'partitions', sortable: false},
                        {label: '整表大小', field: 'size', sortable: false}
                    ]
                });

                infoGrid.renderArray(result.info);
                pane.addChild(infoGrid);

                var columnGrid = new Grid({
                    className: 'dgrid-autoheight',
                    columns: [
                        {label: '字段名', field: 'name', sortable: false},
                        {label: '类型', field: 'type', sortable: false},
                        {label: '注释', field: 'comment', sortable: false}
                    ]
                });

                columnGrid.renderArray(result.columns);
                pane.addChild(columnGrid);

                put(pane.domNode, 'div.task-result-header', '样本数据');

                var rowGrid  = new (declare([Grid, ColumnResizer]))({
                    className: 'dgrid-autoheight',
                    columns: result.tableColumns
                });

                rowGrid.renderArray(result.rows);
                pane.addChild(rowGrid);
            });
        },

        udfList: [
            {name: 'SUBSTRING_INDEX', jar: 'SubStringIndexUDF.jar', clazz: 'com.anjuke.dw.hive.udf.SubStringIndex'},
            {name: 'RANK', jar: 'RankUDF.jar', clazz: 'com.anjuke.dw.hive.udf.Rank'},
            {name: 'MD5', jar: 'MD5UDF.jar', clazz: 'com.anjuke.dw.hive.udf.MD5'}
        ],

        formatOptionUdf: function(name) {
            var self = this;
            var udf = array.filter(self.udfList, function(item) {
                return item.name == name;
            })[0];
            return 'ADD JAR /home/hadoop/dwetl/hiveudf/' + udf.jar + ';\n'
                 + 'CREATE TEMPORARY FUNCTION ' + udf.name + ' AS \'' + udf.clazz + '\';\n';
        },

        formatOptionSet: function(name) {
            var self = this;

            switch (name) {
            case 'reducerCount':
                var nsReducerCount = query('[name="optionReducerCountValue"]')[0];
                return 'SET mapred.reduce.tasks = ' + domAttr.get(nsReducerCount, 'value') + ';\n';

            case 'mapsideJoin':
                return 'SET hive.auto.convert.join = true;\n';

            case 'shark':
                return 'SET dw.engine = shark;\n';
            }
        },

        initOption: function() {
            var self = this;

            var pane = registry.byId('paneOption');

            // udf
            var ulUdf = '<ul class="option-list">';
            array.forEach(self.udfList, function(udf, i) {
                ulUdf += '<li><a href="javascript:void(0);" option_udf="' + udf.name + '">添加</a>'
                       + '<label><input type="checkbox" data-dojo-type="dijit/form/CheckBox" name="optionUdf" value="' + udf.name + '"> ' + udf.name + '</label>'
                       + '</li>';
            });
            ulUdf += '</ul>';

            var fsUdf = new Fieldset({
                title: 'UDF',
                content: ulUdf
            });
            pane.addChild(fsUdf);

            array.forEach(query('[name="optionUdf"]'), function(cb) {
                on(cb, 'change', function() {
                    self.saveOptions();
                });
            });

            array.forEach(query('a[option_udf]'), function(a) {
                on(a, 'click', function() {
                    self.insertOption(self.formatOptionUdf(domAttr.get(a, 'option_udf')));
                });
            });

            // set
            var ulSet = '<ul class="option-list">'
                      + '<li><a href="javascript:void(0);" option_set="reducerCount">添加</a>'
                      + '  <label><input type="checkbox" data-dojo-type="dijit/form/CheckBox" name="optionSet" value="reducerCount"> Reducer数量</label>'
                      + '  <input type="text" name="optionReducerCountValue" value="20" data-dojo-type="dijit/form/NumberSpinner" style="width: 60px;">'
                      + '</li>'

                      + '<li><a href="javascript:void(0);" option_set="mapsideJoin">添加</a>'
                      + '  <label><input type="checkbox" data-dojo-type="dijit/form/CheckBox" name="optionSet" value="mapsideJoin"> Map-side JOIN</label>'
                      + '</li>'

                      + '<li><a href="javascript:void(0);" option_set="shark">添加</a>'
                      + '  <label><input type="checkbox" data-dojo-type="dijit/form/CheckBox" name="optionSet" value="shark"> Shark</label>'
                      + '</li>'
                      + '</ul>';

            var fsSet = new Fieldset({
                title: '配置项',
                content: ulSet,
                style: 'margin-top: 10px;'
            });
            pane.addChild(fsSet);

            array.forEach(query('[name="optionSet"]'), function(cb) {
                on(cb, 'change', function() {
                    self.saveOptions();
                });
            });

            array.forEach(query('a[option_set]'), function(a) {
                on(a, 'click', function() {
                    self.insertOption(self.formatOptionSet(domAttr.get(a, 'option_set')));
                });
            });

            array.forEach(query('[name="optionReducerCountValue"]'), function(ns) {
                on(registry.getEnclosingWidget(ns), 'change', function() {
                    self.saveOptions();
                });
            });

            self.readOptions();
        },

        getOptions: function() {
            var self = this;

            var result = '';

            array.forEach(query('[name="optionUdf"]:checked'), function(cb) {
                result += self.formatOptionUdf(domAttr.get(cb, 'value'));
            });

            array.forEach(query('[name="optionSet"]:checked'), function(cb) {
                result += self.formatOptionSet(domAttr.get(cb, 'value'));
            });

            return result;
        },

        saveOptions: function() {
            var self = this;

            if (!window.localStorage) {
                return;
            }

            var options = {
                udf: [],
                set: []
            };

            array.forEach(query('[name="optionUdf"]:checked'), function(cb) {
                options.udf.push(domAttr.get(cb, 'value'));
            });

            array.forEach(query('[name="optionSet"]:checked'), function(cb) {

                var option = {
                    name: domAttr.get(cb, 'value')
                };

                if (option.name == 'reducerCount') {
                    option.value = domAttr.get(query('[name="optionReducerCountValue"]')[0], 'value');
                }

                options.set.push(option);
            });

            window.localStorage['dw.explorer.options'] = json.stringify(options);
        },

        readOptions: function() {
            var self = this;

            if (!window.localStorage) {
                return;
            }

            var options = {
                udf: [],
                set: []
            };

            try {
                lang.mixin(options, json.parse(window.localStorage['dw.explorer.options']));
            } catch (e) {}

            array.forEach(options.udf, function(name) {
                array.forEach(query('[name="optionUdf"][value="' + name + '"]'), function(cb) {
                    registry.getEnclosingWidget(cb).set('checked', true);
                });
            });

            array.forEach(options.set, function(option) {
                array.forEach(query('[name="optionSet"][value="' + option.name + '"]'), function(cb) {
                    registry.getEnclosingWidget(cb).set('checked', true);
                    if (option.name == 'reducerCount') {
                        registry.getEnclosingWidget(query('[name="optionReducerCountValue"]')[0]).set('value', option.value);
                    }
                });
            });
        },

        insertOption: function(content) {
            var self = this;

            var central = registry.byId('central');
            var pane = central.selectedChildWidget;

            if (typeof pane == 'undefined') {
                alert('请先打开一个文档。');
                return;
            }

            var editor = pane.codeMirror;
            editor.replaceRange(content, editor.getCursor());
        },

        _theEnd: undefined

    });

    return new QueryEditor();

});
