import { useContext } from 'react'
import { DataSourceContext } from '../context/dataSource'

export function useDataSource() {
  const value = useContext(DataSourceContext)
  if (!value) throw new Error('useDataSource must be used within DataSourceProvider')
  return value
}
