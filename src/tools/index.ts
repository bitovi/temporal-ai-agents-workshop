import {
  definition as getWeatherForecastDefinition,
  fn as getWeatherForecastFunction,
} from './getWeatherForecast'

import { definition as getTodaysDateDefinition, fn as getTodaysDateFunction } from './getTodaysDate'

export const toolDefinitions = [getWeatherForecastDefinition, getTodaysDateDefinition]

export const toolFunctions: { [name: string]: Function } = {
  get_weather_forecast: getWeatherForecastFunction,
  get_todays_date: getTodaysDateFunction,
}
