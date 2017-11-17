'use strict';

System.register(['app/core/config', './canvas-metric', './points', 'lodash', 'moment', 'angular', 'app/core/utils/kbn', 'app/core/app_events'], function (_export, _context) {
  "use strict";

  var config, CanvasPanelCtrl, DistinctPoints, _, moment, angular, kbn, appEvents, _createClass, DiscretePanelCtrl;

  function _classCallCheck(instance, Constructor) {
    if (!(instance instanceof Constructor)) {
      throw new TypeError("Cannot call a class as a function");
    }
  }

  function _possibleConstructorReturn(self, call) {
    if (!self) {
      throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
    }

    return call && (typeof call === "object" || typeof call === "function") ? call : self;
  }

  function _inherits(subClass, superClass) {
    if (typeof superClass !== "function" && superClass !== null) {
      throw new TypeError("Super expression must either be null or a function, not " + typeof superClass);
    }

    subClass.prototype = Object.create(superClass && superClass.prototype, {
      constructor: {
        value: subClass,
        enumerable: false,
        writable: true,
        configurable: true
      }
    });
    if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;
  }

  return {
    setters: [function (_appCoreConfig) {
      config = _appCoreConfig.default;
    }, function (_canvasMetric) {
      CanvasPanelCtrl = _canvasMetric.CanvasPanelCtrl;
    }, function (_points) {
      DistinctPoints = _points.default;
    }, function (_lodash) {
      _ = _lodash.default;
    }, function (_moment) {
      moment = _moment.default;
    }, function (_angular) {
      angular = _angular.default;
    }, function (_appCoreUtilsKbn) {
      kbn = _appCoreUtilsKbn.default;
    }, function (_appCoreApp_events) {
      appEvents = _appCoreApp_events.default;
    }],
    execute: function () {
      _createClass = function () {
        function defineProperties(target, props) {
          for (var i = 0; i < props.length; i++) {
            var descriptor = props[i];
            descriptor.enumerable = descriptor.enumerable || false;
            descriptor.configurable = true;
            if ("value" in descriptor) descriptor.writable = true;
            Object.defineProperty(target, descriptor.key, descriptor);
          }
        }

        return function (Constructor, protoProps, staticProps) {
          if (protoProps) defineProperties(Constructor.prototype, protoProps);
          if (staticProps) defineProperties(Constructor, staticProps);
          return Constructor;
        };
      }();

      _export('PanelCtrl', DiscretePanelCtrl = function (_CanvasPanelCtrl) {
        _inherits(DiscretePanelCtrl, _CanvasPanelCtrl);

        function DiscretePanelCtrl($scope, $injector, $q) {
          _classCallCheck(this, DiscretePanelCtrl);

          var _this = _possibleConstructorReturn(this, (DiscretePanelCtrl.__proto__ || Object.getPrototypeOf(DiscretePanelCtrl)).call(this, $scope, $injector, $q));

          _this.data = null;

          // Set and populate defaults
          var panelDefaults = {
            display: 'timeline',
            rowHeight: 50,
            valueMaps: [{ value: 'null', op: '=', text: 'N/A' }],
            mappingTypes: [{ name: 'value to text', value: 1 }, { name: 'range to text', value: 2 }],
            rangeMaps: [{ from: 'null', to: 'null', text: 'N/A' }],
            colorMaps: [{ text: 'N/A', color: '#CCC' }],
            metricNameColor: '#000000',
            valueTextColor: '#000000',
            backgroundColor: 'rgba(128, 128, 128, 0.1)',
            lineColor: 'rgba(128, 128, 128, 1.0)',
            textSize: 24,
            extendLastValue: true,
            writeLastValue: true,
            writeAllValues: false,
			useColorSeries: false,
            writeMetricNames: false,
            showLegend: true,
            showLegendNames: true,
            showLegendValues: true,
            showLegendPercent: true,
            highlightOnMouseover: true,
            legendSortBy: '-ms'
          };
          _.defaults(_this.panel, panelDefaults);
          _this.externalPT = false;

          _this.events.on('init-edit-mode', _this.onInitEditMode.bind(_this));
          _this.events.on('render', _this.onRender.bind(_this));
          _this.events.on('data-received', _this.onDataReceived.bind(_this));
          _this.events.on('data-error', _this.onDataError.bind(_this));
          _this.events.on('refresh', _this.onRefresh.bind(_this));

          _this.updateColorInfo();
          _this.onConfigChanged();
          return _this;
        }

        _createClass(DiscretePanelCtrl, [{
          key: 'onDataError',
          value: function onDataError(err) {
            console.log("onDataError", err);
          }
        }, {
          key: 'onInitEditMode',
          value: function onInitEditMode() {
            this.addEditorTab('Options', 'public/plugins/natel-discrete-panel/editor.html', 1);
            this.addEditorTab('Legend', 'public/plugins/natel-discrete-panel/legend.html', 3);
            this.addEditorTab('Colors', 'public/plugins/natel-discrete-panel/colors.html', 4);
            this.addEditorTab('Mappings', 'public/plugins/natel-discrete-panel/mappings.html', 5);
            this.editorTabIndex = 1;
            this.refresh();
          }
        }, {
          key: 'onRender',
          value: function onRender() {
            var _this2 = this;

            if (this.data == null || !this.context) {
              return;
            }

            //   console.log( 'render', this.data);

            var rect = this.wrap.getBoundingClientRect();

            var rows = this.data.length;
            var rowHeight = this.panel.rowHeight;

            var height = rowHeight * rows;
            var width = rect.width;
            this.canvas.width = width;
            this.canvas.height = height;

            var ctx = this.context;
            ctx.lineWidth = 1;
            ctx.textBaseline = 'middle';
            ctx.font = this.panel.textSize + 'px "Open Sans", Helvetica, Arial, sans-serif';

            // ctx.shadowOffsetX = 1;
            // ctx.shadowOffsetY = 1;
            // ctx.shadowColor = "rgba(0,0,0,0.3)";
            // ctx.shadowBlur = 3;

            var top = 0;
			var offset = 100;
            var elapsed = this.range.to - this.range.from - offset;

            _.forEach(this.data, function (metric) {
              var centerV = top + rowHeight / 2;

              // The no-data line
              ctx.fillStyle = _this2.panel.backgroundColor;
              ctx.fillRect(0, top, width, rowHeight);

              /*if(!this.panel.writeMetricNames) {
                ctx.fillStyle = "#111111";
                ctx.textAlign = 'left';
                ctx.fillText("No Data", 10, centerV);
              }*/
              if (_this2.isTimeline) {
                var lastBS = 0;
                var point = metric.changes[0];
                for (var i = 0; i < metric.changes.length; i++) {
                  point = metric.changes[i];
                  if (point.start <= _this2.range.to) {
                    var xt = Math.max(point.start - _this2.range.from, 0);
                    point.x = offset + (xt / elapsed * (width-offset));
                    ctx.fillStyle = _this2.getCondColor(point);
                    ctx.fillRect(point.x, top, width, rowHeight);

                    if (_this2.panel.writeAllValues) {
                      ctx.fillStyle = _this2.panel.valueTextColor;
                      ctx.textAlign = 'left';
                      ctx.fillText(point.val, point.x + 7, centerV);
                    }
                    lastBS = point.x;
                  }
                }
              } else if (_this2.panel.display == 'stacked') {
                var point = null;
                var start = _this2.range.from;
                for (var i = 0; i < metric.legendInfo.length; i++) {
                  point = metric.legendInfo[i];

                  var xt = Math.max(start - _this2.range.from, 0);
                  point.x = xt / elapsed * width;
                  ctx.fillStyle = _this2.getCondColor(point);
                  ctx.fillRect(point.x, top, width, rowHeight);

                  if (_this2.panel.writeAllValues) {
                    ctx.fillStyle = _this2.panel.valueTextColor;
                    ctx.textAlign = 'left';
                    ctx.fillText(point.val, point.x + 7, centerV);
                  }

                  start += point.ms;
                }
              } else {
                console.log("Not supported yet...", _this2);
              }

              if (top > 0) {
                ctx.strokeStyle = _this2.panel.lineColor;
                ctx.beginPath();
                ctx.moveTo(0, top);
                ctx.lineTo(width, top);
                ctx.stroke();
              }

              ctx.fillStyle = "#000000";

              if (_this2.panel.writeMetricNames && (!_this2.panel.highlightOnMouseover || _this2.panel.highlightOnMouseover)) {
                ctx.fillStyle = _this2.panel.metricNameColor;
                ctx.textAlign = 'left';
                ctx.fillText(metric.name, 10, centerV);
              }

              ctx.textAlign = 'right';

              if (_this2.mouse.down == null) {
                if (_this2.panel.highlightOnMouseover && _this2.mouse.position != null) {

		  var next = null;
		  if (_this2.isTimeline) {
			  if (_this2.panel.useColorSeries) {
				point = metric.changes[0];
				for (var i = 0; i < metric.changes.length; i++) {
					if (metric.changes[i].x > _this2.mouse.position.x) {
						next = metric.changes[i];
						break;
					}
					point = metric.changes[i];
				}  
			  } else {
                    		point = metric.changes[0];
                    		for (var i = 0; i < metric.changes.length; i++) {
                      			if (metric.changes[i].start > _this2.mouse.position.ts) {
                        			next = metric.changes[i];
                        			break;
                      			}
                      			point = metric.changes[i];
                    		}
			  }
                  } else if (_this2.panel.display == 'stacked') {
                    point = metric.legendInfo[0];
                    for (var i = 0; i < metric.legendInfo.length; i++) {
                      if (metric.legendInfo[i].x > _this2.mouse.position.x) {
                        next = metric.legendInfo[i];
                        break;
                      }
                      point = metric.legendInfo[i];
                    }
                  }

                  // Fill canvas using 'destination-out' and alpha at 0.05
                  ctx.globalCompositeOperation = 'destination-out';
                  ctx.fillStyle = "rgba(255, 255, 255, 0.5)";
                  ctx.beginPath();
                  ctx.fillRect(0, top, point.x, rowHeight);
                  ctx.fill();

                  if (next != null) {
                    ctx.beginPath();
                    ctx.fillRect(next.x, top, width, rowHeight);
                    ctx.fill();
                  }
                  ctx.globalCompositeOperation = 'source-over';

                  // Now Draw the value
                  ctx.fillStyle = "#000000";
                  ctx.textAlign = 'left';
                  ctx.fillText(point.val, point.x + 7, centerV);
                } else if (_this2.panel.writeLastValue) {
                  ctx.fillText(point.val, width - 7, centerV);
                }
              }

              top += rowHeight;
            });

            if (this.isTimeline && this.mouse.position != null) {
              if (this.mouse.down != null) {
                var xmin = Math.min(this.mouse.position.x, this.mouse.down.x);
                var xmax = Math.max(this.mouse.position.x, this.mouse.down.x);

                // Fill canvas using 'destination-out' and alpha at 0.05
                ctx.globalCompositeOperation = 'destination-out';
                ctx.fillStyle = "rgba(255, 255, 255, 0.6)";
                ctx.beginPath();
                ctx.fillRect(0, 0, xmin, height);
                ctx.fill();

                ctx.beginPath();
                ctx.fillRect(xmax, 0, width, height);
                ctx.fill();
                ctx.globalCompositeOperation = 'source-over';
              } else {
                ctx.strokeStyle = '#111';
                ctx.beginPath();
                ctx.moveTo(this.mouse.position.x, 0);
                ctx.lineTo(this.mouse.position.x, height);
                ctx.lineWidth = 3;
                ctx.stroke();

                ctx.beginPath();
                ctx.moveTo(this.mouse.position.x, 0);
                ctx.lineTo(this.mouse.position.x, height);
                ctx.strokeStyle = '#e22c14';
                ctx.lineWidth = 2;
                ctx.stroke();

                if (this.externalPT && rows > 1) {
                  ctx.beginPath();
                  ctx.arc(this.mouse.position.x, this.mouse.position.y, 3, 0, 2 * Math.PI, false);
                  ctx.fillStyle = '#e22c14';
                  ctx.fill();
                  ctx.lineWidth = 1;
                  ctx.strokeStyle = '#111';
                  ctx.stroke();
                }
              }
            }
          }
        }, {
          key: 'showLegandTooltip',
          value: function showLegandTooltip(pos, info) {
            var body = '<div class="graph-tooltip-time">' + info.val + '</div>';

            body += "<center>";
            if (info.count > 1) {
              body += info.count + " times<br/>for<br/>";
            }
            body += moment.duration(info.ms).humanize();
            if (info.count > 1) {
              body += "<br/>total";
            }
            body += "</center>";

            this.$tooltip.html(body).place_tt(pos.pageX + 20, pos.pageY);
          }
        }, {
          key: 'clearTT',
          value: function clearTT() {
            this.$tooltip.detach();
          }
        }, {
          key: 'formatValue',
          value: function formatValue(val, stats) {

            if (_.isNumber(val) && this.panel.rangeMaps) {
              for (var i = 0; i < this.panel.rangeMaps.length; i++) {
                var map = this.panel.rangeMaps[i];

                // value/number to range mapping
                var from = parseFloat(map.from);
                var to = parseFloat(map.to);
                if (to >= val && from <= val) {
                  return map.text;
                }
              }
            }

            var isNull = _.isNil(val);
            if (!isNull && !_.isString(val)) {
              val = val.toString(); // convert everything to a string
            }

            for (var i = 0; i < this.panel.valueMaps.length; i++) {
              var map = this.panel.valueMaps[i];
              // special null case
              if (map.value === 'null') {
                if (isNull) {
                  return map.text;
                }
                continue;
              }

              if (val == map.value) {
                return map.text;
              }
            }

            if (isNull) {
              return "";
            }
            return val;
          }
        }, {
          key: 'getColor',
          value: function getColor(val) {
            if (_.has(this.colorMap, val)) {
              return this.colorMap[val];
            }

            return '#E8DBAE';
          }
        }, {
          key: 'getCondColor',
          value: function getCondColor(point) {
            if (_.has(this.colorMap, point.val)) {
              return this.colorMap[point.val];
            }

			var re = new RegExp("^#[a-fA-F0-9][a-fA-F0-9][a-fA-F0-9][a-fA-F0-9][a-fA-F0-9][a-fA-F0-9]$");
			if (re.test(point.val2)) {
				return point.val2;
			} else {
				return '#FBFBFB';
			}
          }
        }, {
          key: 'randomColor',
          value: function randomColor() {
            var letters = 'ABCDE'.split('');
            var color = '#';
            for (var i = 0; i < 3; i++) {
              color += letters[Math.floor(Math.random() * letters.length)];
            }
            return color;
          }
        }, {
          key: 'hashCode',
          value: function hashCode(str) {
            var hash = 0;
            if (str.length == 0) return hash;
            for (var i = 0; i < str.length; i++) {
              var char = str.charCodeAt(i);
              hash = (hash << 5) - hash + char;
              hash = hash & hash; // Convert to 32bit integer
            }
            return hash;
          }
        }, {
          key: 'issueQueries',
          value: function issueQueries(datasource) {
            this.datasource = datasource;

            if (!this.panel.targets || this.panel.targets.length === 0) {
              return this.$q.when([]);
            }

            // make shallow copy of scoped vars,
            // and add built in variables interval and interval_ms
            var scopedVars = Object.assign({}, this.panel.scopedVars, {
              "__interval": { text: this.interval, value: this.interval },
              "__interval_ms": { text: this.intervalMs, value: this.intervalMs }
            });

            var range = this.range;
            var rangeRaw = this.rangeRaw || this.range.raw;
            if (this.panel.expandFromQueryS > 0) {
              range = {
                from: this.range.from.clone(),
                to: this.range.to
              };
              range.from.subtract(this.panel.expandFromQueryS, 's');

              rangeRaw = {
                from: range.from.format(),
                to: rangeRaw.to
              };
              range.raw = rangeRaw;
            }

            var metricsQuery = {
              panelId: this.panel.id,
              range: range,
              rangeRaw: rangeRaw,
              interval: this.interval,
              intervalMs: this.intervalMs,
              targets: this.panel.targets,
              format: this.panel.renderer === 'png' ? 'png' : 'json',
              maxDataPoints: this.resolution,
              scopedVars: scopedVars,
              cacheTimeout: this.panel.cacheTimeout
            };

            return datasource.query(metricsQuery);
          }
        }, {
          key: 'onDataReceived',
          value: function onDataReceived(dataList) {
            var _this3 = this;

            $(this.canvas).css('cursor', 'pointer');

            //    console.log('GOT', dataList);

            var data = [];
			if (_this3.panel.useColorSeries) {
				for (var k = 0; k < dataList.length; k=k+4) {
					var metric = dataList[k];
					var metric2 = dataList[k+1];
					var metric3 = dataList[k+2];
					var metric4 = dataList[k+3];
					var res = new DistinctPoints(metric.target);
					for (var j = 0; j < metric.datapoints.length; j++) {
						var point = metric.datapoints[j];
						var point2 = metric2.datapoints[j];
						var point3 = metric3.datapoints[j];
						var point4 = metric4.datapoints[j];
						res.add(point[1], _this3.formatValue(point[0]), _this3.formatValue(point2[0]), _this3.formatValue(point3[0]), _this3.formatValue(point4[0]));
					}
					res.finish(_this3);
					data.push(res);
				}
			} else {
				_.forEach(dataList, function (metric) {
				  if ('table' === metric.type) {
					if ('time' != metric.columns[0].type) {
					  throw 'Expected a time column from the table format';
					}

					var last = null;
					for (var i = 1; i < metric.columns.length; i++) {
					  var res = new DistinctPoints(metric.columns[i].text);
					  for (var j = 0; j < metric.rows.length; j++) {
						var row = metric.rows[j];
						res.add(row[0], _this3.formatValue(row[i]));
					  }
					  res.finish(_this3);
					  data.push(res);
					}
				  } else {
					var res = new DistinctPoints(metric.target);
					_.forEach(metric.datapoints, function (point) {
					  res.add(point[1], _this3.formatValue(point[0]));
					});
					res.finish(_this3);
					data.push(res);
				  }
				});
			}
			
            this.data = data;

            this.onRender();

            //console.log( 'data', dataList, this.data);
          }
        }, {
          key: 'removeColorMap',
          value: function removeColorMap(map) {
            var index = _.indexOf(this.panel.colorMaps, map);
            this.panel.colorMaps.splice(index, 1);
            this.updateColorInfo();
          }
        }, {
          key: 'updateColorInfo',
          value: function updateColorInfo() {
            var cm = {};
            for (var i = 0; i < this.panel.colorMaps.length; i++) {
              var m = this.panel.colorMaps[i];
              if (m.text) {
                cm[m.text] = m.color;
              }
            }
            this.colorMap = cm;
            this.render();
          }
        }, {
          key: 'addColorMap',
          value: function addColorMap(what) {
            var _this4 = this;

            if (what == 'curent') {
              _.forEach(this.data, function (metric) {
                if (metric.legendInfo) {
                  _.forEach(metric.legendInfo, function (info) {
                    if (!_.has(info.val)) {
                      _this4.panel.colorMaps.push({ text: info.val, color: _this4.getColor(info.val) });
                    }
                  });
                }
              });
            } else {
              this.panel.colorMaps.push({ text: '???', color: this.randomColor() });
            }
            this.updateColorInfo();
          }
        }, {
          key: 'removeValueMap',
          value: function removeValueMap(map) {
            var index = _.indexOf(this.panel.valueMaps, map);
            this.panel.valueMaps.splice(index, 1);
            this.render();
          }
        }, {
          key: 'addValueMap',
          value: function addValueMap() {
            this.panel.valueMaps.push({ value: '', op: '=', text: '' });
          }
        }, {
          key: 'removeRangeMap',
          value: function removeRangeMap(rangeMap) {
            var index = _.indexOf(this.panel.rangeMaps, rangeMap);
            this.panel.rangeMaps.splice(index, 1);
            this.render();
          }
        }, {
          key: 'addRangeMap',
          value: function addRangeMap() {
            this.panel.rangeMaps.push({ from: '', to: '', text: '' });
          }
        }, {
          key: 'onConfigChanged',
          value: function onConfigChanged() {
            //console.log( "Config changed...");
            this.isTimeline = true; //this.panel.display == 'timeline';
            this.render();
          }
        }, {
          key: 'getLegendDisplay',
          value: function getLegendDisplay(info, metric) {
            var disp = info.val;
            if (this.panel.showLegendPercent || this.panel.showLegendCounts || this.panel.showLegendTime) {
              disp += " (";
              var hassomething = false;
              if (this.panel.showLegendTime) {
                disp += moment.duration(info.ms).humanize();
                hassomething = true;
              }

              if (this.panel.showLegendPercent) {
                if (hassomething) {
                  disp += ", ";
                }

                var dec = this.panel.legendPercentDecimals;
                if (_.isNil(dec)) {
                  if (info.per > .98 && metric.changes.length > 1) {
                    dec = 2;
                  } else if (info.per < 0.02) {
                    dec = 2;
                  } else {
                    dec = 0;
                  }
                }
                disp += kbn.valueFormats.percentunit(info.per, dec);
                hassomething = true;
              }

              if (this.panel.showLegendCounts) {
                if (hassomething) {
                  disp += ", ";
                }
                disp += info.count + "x";
              }
              disp += ")";
            }
            return disp;
          }
        }, {
          key: 'showTooltip',
          value: function showTooltip(evt, point, isExternal) {
            var from = point.start;
            var to = point.start + point.ms;
            var time = point.ms;
            var val = point.val;

            if (this.mouse.down != null) {
              from = Math.min(this.mouse.down.ts, this.mouse.position.ts);
              to = Math.max(this.mouse.down.ts, this.mouse.position.ts);
              time = to - from;
              val = "Zoom To:";
            }

            var body = '<div class="graph-tooltip-time">' + val + '</div>';

            body += "<center>";
            body += this.dashboard.formatDate(moment(from)) + "<br/>";
            body += "to<br/>";
            body += this.dashboard.formatDate(moment(to)) + "<br/><br/>";
            body += moment.duration(time).humanize() + "<br/>";
            body += "</center>";

            var pageX = 0;
            var pageY = 0;
            if (isExternal) {
              var rect = this.canvas.getBoundingClientRect();
              pageY = rect.top + evt.pos.panelRelY * rect.height;
              if (pageY < 0 || pageY > $(window).innerHeight()) {
                // Skip Hidden tooltip
                this.$tooltip.detach();
                return;
              }
              pageY += $(window).scrollTop();

              var elapsed = this.range.to - this.range.from;
              var pX = (evt.pos.x - this.range.from) / elapsed;
              pageX = rect.left + pX * rect.width;
            } else {
              pageX = evt.evt.pageX;
              pageY = evt.evt.pageY;
            }

            this.$tooltip.html(body).place_tt(pageX + 20, pageY + 5);
          }
        }, {
          key: 'showCustomTooltip',
          value: function showCustomTooltip(posX, posY, point) {

			var body = '<div><table style="border-spacing: 10px; border-collapse: separate;">';
			if(point.val3 && point.val3 > 0.0){
				body = body +'<tr><td><b>Project:  </b></td><td>';
				body = body + point.val;
				body = body +'</td></tr><tr><td><b>Daily Rate:  </b></td><td>€';
				body = body + point.val3;
				body = body +'</td></tr>';
			} else {
				var body = '<tr><td><b>';
				body = body + point.val;
				body = body +'</b></td></tr>';
			}
			
			if(point.val4){
				body = body +'</tr><tr><td><b>Notes:  </b></td><td>';
				body = body + point.val4;
				body = body +'</td></tr>';
			} 
			body = body +'</table></div>';
			
			var rect = this.wrap.getBoundingClientRect();
            var pageX = rect.x + posX;
            var pageY = rect.y + posY;
		
            this.$tooltip.html(body).place_tt(pageX + 20, pageY + 5);
          }
        }, {
          key: 'onGraphHover',
          value: function onGraphHover(evt, showTT, isExternal) {
            this.externalPT = false;

			if (this.panel.useColorSeries) {
//window.alert("evt: " + JSON.stringify(this.wrap.getBoundingClientRect()));

				var hover = null; 
				if (this.isTimeline) {
					var rect = this.wrap.getBoundingClientRect();
					var x = evt.evt.pageX - rect.x;
					var y = evt.evt.pageY - rect.y;
					var j = Math.floor(y / this.panel.rowHeight);
					if (j < 0) {
						j = 0;
					}
					if (j >= this.data.length) {
						j = this.data.length - 1;
					}
					hover = this.data[j].changes[0];
					
					for (var i = 0; i < this.data[j].changes.length; i++) {
					  if (this.data[j].changes[i].x > x) {
						break;
					  }
					  hover = this.data[j].changes[i];
					}
				//	window.alert("x:" + x + "  y:" + y + "  hover: " + JSON.stringify(hover));

					this.showCustomTooltip(x, y, hover);

					this.onRender(); // refresh the view
				}
			} else {	
				if (this.data && this.data.length) {
				  var hover = null;
				  var j = Math.floor(this.mouse.position.y / this.panel.rowHeight);
				  if (j < 0) {
					j = 0;
				  }
				  if (j >= this.data.length) {
					j = this.data.length - 1;
				  }

				  if (this.isTimeline) {
					hover = this.data[j].changes[0];
					for (var i = 0; i < this.data[j].changes.length; i++) {
					  if (this.data[j].changes[i].start > this.mouse.position.ts) {
						break;
					  }
					  hover = this.data[j].changes[i];
					}
					this.hoverPoint = hover;

					if (showTT) {
					  this.externalPT = isExternal;
					  this.showTooltip(evt, hover, isExternal);
					}
					this.onRender(); // refresh the view
				  } else if (!isExternal) {
					if (this.panel.display == 'stacked') {
					  hover = this.data[j].legendInfo[0];
					  for (var i = 0; i < this.data[j].legendInfo.length; i++) {
						if (this.data[j].legendInfo[i].x > this.mouse.position.x) {
						  break;
						}
						hover = this.data[j].legendInfo[i];
					  }
					  this.hoverPoint = hover;
					  this.onRender(); // refresh the view

					  if (showTT) {
						this.externalPT = isExternal;
						this.showLegandTooltip(evt.evt, hover);
					  }
					}
				  }
				} else {
				  this.$tooltip.detach(); // make sure it is hidden
				}
			}
          }
        }, {
          key: 'onMouseClicked',
          value: function onMouseClicked(where) {
			  var hover = null;  
 //window.alert("where: " + JSON.stringify(where));

			  if (this.isTimeline) {
			    var j = Math.floor(where.y / this.panel.rowHeight);
			   
				if (j < 0) {
					j = 0;
				}
				if (j >= this.data.length) {
					j = this.data.length - 1;
				}
				hover = this.data[j].changes[0];
				
				for (var i = 0; i < this.data[j].changes.length; i++) {
				  if (this.data[j].changes[i].x > where.x) {
					break;
				  }
				  hover = this.data[j].changes[i];
				}
				
				this.showCustomTooltip(where.x, where.y, hover);

				this.onRender(); // refresh the view
			  }
          }
        }, {
          key: 'onMouseSelectedRange',
          value: function onMouseSelectedRange(range) {
            this.timeSrv.setTime(range);
            this.clear();
          }
        }, {
          key: 'clear',
          value: function clear() {
            this.mouse.position = null;
            this.mouse.down = null;
            this.hoverPoint = null;
            $(this.canvas).css('cursor', 'wait');
            appEvents.emit('graph-hover-clear');
            this.render();
          }
        }]);

        return DiscretePanelCtrl;
      }(CanvasPanelCtrl));

      DiscretePanelCtrl.templateUrl = 'module.html';

      _export('PanelCtrl', DiscretePanelCtrl);
    }
  };
});
//# sourceMappingURL=module.js.map
